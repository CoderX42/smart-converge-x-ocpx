package com.yzytech.scx.ocpx.audit.store;

import com.alibaba.fastjson2.JSON;
import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptedWireAuditWal {

	private static final int MAGIC = 0x53435857;
	private static final byte VERSION = 1;
	private static final DateTimeFormatter SEGMENT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final WireAuditProperties properties;
	private final OcpxKeyRingCrypto crypto;
	private final AtomicLong sequence = new AtomicLong();

	private Path directory;
	private Path activePath;
	private FileChannel channel;
	private LocalDateTime openedAt;
	private ScheduledExecutorService flusher;
	private long durableSequence;
	private IOException flushFailure;
	private long lastCapacityCheckNanos;
	private long nextOpenAttemptNanos;

	@PostConstruct
	void initialize() {
		if (!properties.isEnabled())
			return;
		ensureOpen();
		flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "ocpx-audit-wal-flusher");
			thread.setDaemon(true);
			return thread;
		});
		long flushInterval = Math.max(1L, properties.getFlushIntervalMillis());
		flusher.scheduleAtFixedRate(this::flushPending, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
	}

	public synchronized boolean append(WireAuditRecord record) {
		if (!properties.isEnabled())
			return false;
		try {
			if (!ensureOpen())
				return false;
			rotateIfRequired();
			long seq = sequence.incrementAndGet();
			String keyId = crypto.activeKeyId();
			byte[] aad = walAad(seq, keyId);
			OcpxKeyRingCrypto.SealedValue sealed = crypto.encrypt(JSON.toJSONBytes(record),
					OcpxKeyRingCrypto.PURPOSE_AUDIT, aad);
			byte[] key = keyId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			CRC32 crc = new CRC32();
			crc.update(sealed.cipherText());
			ByteBuffer frame = ByteBuffer.allocate(Integer.BYTES + 1 + Long.BYTES + Short.BYTES + 1 + Integer.BYTES
					+ Integer.BYTES + key.length + sealed.nonce().length + sealed.cipherText().length);
			frame.putInt(MAGIC).put(VERSION).putLong(seq).putShort((short) key.length).put((byte) sealed.nonce().length)
				.putInt(sealed.cipherText().length).putInt((int) crc.getValue()).put(key).put(sealed.nonce())
				.put(sealed.cipherText()).flip();
			while (frame.hasRemaining())
				channel.write(frame);
			while (durableSequence < seq && flushFailure == null)
				wait(Math.max(1L, properties.getFlushIntervalMillis() * 4L));
			if (flushFailure != null)
				throw flushFailure;
			if (System.nanoTime() - lastCapacityCheckNanos >= TimeUnit.SECONDS.toNanos(10)) {
				warnCapacity();
				lastCapacityCheckNanos = System.nanoTime();
			}
			return true;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.error("OCPX_AUDIT_WAL_APPEND_INTERRUPTED", exception);
			return false;
		}
		catch (Exception exception) {
			log.error("OCPX_AUDIT_WAL_APPEND_FAILED", exception);
			return false;
		}
	}

	public long rotateAndReplay(WireAuditRepository repository) {
		if (!properties.isEnabled())
			return 0L;
		long replayed = 0L;
		try {
			List<Path> segments;
			synchronized (this) {
				if (ensureOpen())
					rotate();
				if (directory == null || !Files.isDirectory(directory))
					return 0L;
				try (var stream = Files.list(directory)) {
					segments = stream.filter(path -> path.getFileName().toString().endsWith(".ready"))
						.sorted(Comparator.comparing(Path::toString))
						.toList();
				}
			}
			for (Path segment : segments) {
				ReplayResult result = replay(segment, repository);
				replayed += result.records();
				if (result.completed()) {
					Files.deleteIfExists(segment);
				}
			}
		}
		catch (Exception exception) {
			log.error("OCPX_AUDIT_WAL_REPLAY_FAILED", exception);
		}
		return replayed;
	}

	private ReplayResult replay(Path segment, WireAuditRepository repository) {
		long replayed = 0L;
		try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(segment)))) {
			while (input.available() > 0) {
				{
					int magic = input.readInt();
					byte version = input.readByte();
					long seq = input.readLong();
					int keyLength = input.readUnsignedShort();
					int nonceLength = input.readUnsignedByte();
					int cipherLength = input.readInt();
					long expectedCrc = Integer.toUnsignedLong(input.readInt());
					long maximumFrameBytes = Math.min(Integer.MAX_VALUE - 8L, properties.getWalMaxBytes());
					if (magic != MAGIC || version != VERSION || keyLength > 128 || nonceLength != 12
							|| cipherLength <= 0 || cipherLength > maximumFrameBytes) {
						throw new IOException("invalid audit WAL frame header");
					}
					String keyId = new String(input.readNBytes(keyLength), java.nio.charset.StandardCharsets.UTF_8);
					byte[] nonce = input.readNBytes(nonceLength);
					byte[] cipherText = input.readNBytes(cipherLength);
					if (nonce.length != nonceLength || cipherText.length != cipherLength) {
						throw new EOFException("partial audit WAL frame");
					}
					CRC32 crc = new CRC32();
					crc.update(cipherText);
					if (crc.getValue() != expectedCrc) {
						throw new IOException("audit WAL CRC mismatch");
					}
					byte[] clear = crypto.decrypt(new OcpxKeyRingCrypto.SealedValue(keyId, VERSION, nonce, cipherText),
							OcpxKeyRingCrypto.PURPOSE_AUDIT, walAad(seq, keyId));
					WireAuditRecord record = JSON.parseObject(clear, WireAuditRecord.class);
					if ("PENDING".equals(record.getResultStatus()) && record.getCapturedAt() != null
							&& record.getCapturedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
						record.setResultStatus("INTERRUPTED");
						record.setRawState("INTERRUPTED");
						record.setErrorCode("PROCESS_INTERRUPTED");
						record.setFinishedAt(LocalDateTime.now());
					}
					record.setCaptureSource("WAL_REPLAY");
					try {
						repository.upsert(record);
					}
					catch (RuntimeException databaseError) {
						throw new ReplayDeferredException(databaseError);
					}
					replayed++;
				}
			}
			return new ReplayResult(true, replayed);
		}
		catch (ReplayDeferredException exception) {
			log.warn("OCPX_AUDIT_WAL_REPLAY_DEFERRED file:{}", segment.getFileName(), exception.getCause());
			return new ReplayResult(false, replayed);
		}
		catch (Exception exception) {
			log.error("OCPX_AUDIT_WAL_SEGMENT_CORRUPT file:{}", segment.getFileName(), exception);
			try {
				Files.move(segment, segment.resolveSibling(segment.getFileName() + ".corrupt"),
						StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException moveError) {
				log.error("OCPX_AUDIT_WAL_QUARANTINE_FAILED file:{}", segment.getFileName(), moveError);
			}
			return new ReplayResult(false, replayed);
		}
	}

	public synchronized long backlogBytes() {
		if (!properties.isEnabled() || directory == null)
			return 0L;
		try (var stream = Files.walk(directory)) {
			return stream.filter(Files::isRegularFile).mapToLong(path -> {
				try {
					return Files.size(path);
				}
				catch (IOException ignored) {
					return 0L;
				}
			}).sum();
		}
		catch (IOException exception) {
			return -1L;
		}
	}

	public synchronized long oldestBacklogAgeSeconds() {
		if (!properties.isEnabled() || directory == null)
			return 0L;
		try (var stream = Files.list(directory)) {
			long oldest = stream.filter(Files::isRegularFile).mapToLong(path -> {
				try {
					return Files.getLastModifiedTime(path).toMillis();
				}
				catch (IOException ignored) {
					return System.currentTimeMillis();
				}
			}).min().orElse(System.currentTimeMillis());
			return Math.max(0L, (System.currentTimeMillis() - oldest) / 1000L);
		}
		catch (IOException exception) {
			return -1L;
		}
	}

	private static final class ReplayDeferredException extends RuntimeException {

		private ReplayDeferredException(RuntimeException cause) {
			super(cause);
		}

	}

	private record ReplayResult(boolean completed, long records) {
	}

	private void rotateIfRequired() throws IOException {
		if (channel.size() >= properties.getSegmentMaxBytes()
				|| !openedAt.truncatedTo(ChronoUnit.HOURS).equals(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS))) {
			rotate();
		}
	}

	private void rotate() throws IOException {
		if (channel != null) {
			forceChannel();
			channel.close();
			Files.move(activePath, readyPath(activePath), StandardCopyOption.REPLACE_EXISTING);
		}
		openSegment();
	}

	private void flushPending() {
		synchronized (this) {
			if (channel == null || durableSequence >= sequence.get() || flushFailure != null)
				return;
			try {
				forceChannel();
			}
			catch (IOException exception) {
				flushFailure = exception;
				log.error("OCPX_AUDIT_WAL_FLUSH_FAILED", exception);
				notifyAll();
			}
		}
	}

	private void forceChannel() throws IOException {
		channel.force(false);
		durableSequence = sequence.get();
		notifyAll();
	}

	private void openSegment() throws IOException {
		openedAt = LocalDateTime.now();
		activePath = directory.resolve("wire-" + SEGMENT_TIME.format(openedAt) + "-" + System.nanoTime() + ".open");
		channel = FileChannel.open(activePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	/** WAL 故障不能阻断业务；最多每十秒重试一次，期间由调用方同步回退 MySQL。 */
	private boolean ensureOpen() {
		if (channel != null && channel.isOpen() && flushFailure == null)
			return true;
		long now = System.nanoTime();
		if (now < nextOpenAttemptNanos)
			return false;
		nextOpenAttemptNanos = now + TimeUnit.SECONDS.toNanos(10);
		closeFailedChannel();
		try {
			directory = Path.of(properties.getWalPath()).toAbsolutePath().normalize();
			Files.createDirectories(directory);
			try (var stream = Files.list(directory)) {
				for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".open")).toList()) {
					Files.move(file, readyPath(file), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			flushFailure = null;
			openSegment();
			nextOpenAttemptNanos = 0L;
			return true;
		}
		catch (Exception exception) {
			log.error("OCPX_AUDIT_WAL_UNAVAILABLE_FALLING_BACK_TO_DATABASE", exception);
			return false;
		}
	}

	private void closeFailedChannel() {
		if (channel == null)
			return;
		try {
			channel.close();
		}
		catch (IOException exception) {
			log.warn("OCPX_AUDIT_WAL_FAILED_CHANNEL_CLOSE", exception);
		}
		finally {
			channel = null;
		}
	}

	private Path readyPath(Path open) {
		String name = open.getFileName().toString();
		return open.resolveSibling(name.substring(0, name.length() - ".open".length()) + ".ready");
	}

	private byte[] walAad(long seq, String keyId) {
		return ("wal|" + seq + "|" + keyId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private void warnCapacity() throws IOException {
		long total;
		try (var stream = Files.walk(directory)) {
			total = stream.filter(Files::isRegularFile).mapToLong(path -> {
				try {
					return Files.size(path);
				}
				catch (IOException ignored) {
					return 0L;
				}
			}).sum();
		}
		double ratio = (double) total / properties.getWalMaxBytes();
		if (ratio >= .95)
			log.error("OCPX_AUDIT_WAL_CAPACITY_CRITICAL bytes:{} ratio:{}", total, ratio);
		else if (ratio >= .85)
			log.error("OCPX_AUDIT_WAL_CAPACITY_HIGH bytes:{} ratio:{}", total, ratio);
		else if (ratio >= .70)
			log.warn("OCPX_AUDIT_WAL_CAPACITY_WARNING bytes:{} ratio:{}", total, ratio);
		if (total >= properties.getWalMaxBytes()) {
			throw new IOException("encrypted audit WAL quota exhausted");
		}
	}

	@PreDestroy
	synchronized void close() {
		if (flusher != null)
			flusher.shutdownNow();
		if (channel == null)
			return;
		try {
			forceChannel();
			channel.close();
		}
		catch (IOException exception) {
			log.error("OCPX_AUDIT_WAL_CLOSE_FAILED", exception);
		}
	}

}
