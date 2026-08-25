package com.yzytech.scx.ocpx.audit.store;

import com.yzytech.scx.ocpx.audit.OcpxKeyRingCrypto;
import com.yzytech.scx.ocpx.audit.config.WireAuditProperties;
import com.yzytech.scx.ocpx.audit.model.WireAuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EncryptedWireAuditWalTest {

	@TempDir
	Path directory;

	@Test
	void durablyReplaysPendingFramesAsInterruptedAndIdempotentUpserts() {
		WireAuditProperties properties = new WireAuditProperties();
		properties.setEnabled(true);
		properties.setWalPath(directory.toString());
		properties.setWalMaxBytes(10_000_000);
		properties.setSegmentMaxBytes(1_000_000);
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "local-dev-key-01"), List.of("v1"));
		EncryptedWireAuditWal wal = new EncryptedWireAuditWal(properties, crypto);
		wal.initialize();
		WireAuditRecord record = WireAuditRecord.builder()
			.id("a".repeat(32))
			.stageCode("MEDIA_INGRESS")
			.resultStatus("PENDING")
			.rawState("PENDING")
			.capturedAt(LocalDateTime.now().minusMinutes(6))
			.build();
		assertThat(wal.append(record)).isTrue();
		WireAuditRepository repository = mock(WireAuditRepository.class);

		wal.rotateAndReplay(repository);

		ArgumentCaptor<WireAuditRecord> captor = ArgumentCaptor.forClass(WireAuditRecord.class);
		verify(repository, atLeastOnce()).upsert(captor.capture());
		assertThat(captor.getValue().getResultStatus()).isEqualTo("INTERRUPTED");
		assertThat(captor.getValue().getCaptureSource()).isEqualTo("WAL_REPLAY");
		wal.close();
	}

	@Test
	void keepsValidSegmentsForRetryWhenDatabaseIsUnavailable() throws Exception {
		WireAuditProperties properties = new WireAuditProperties();
		properties.setEnabled(true);
		properties.setWalPath(directory.toString());
		properties.setWalMaxBytes(10_000_000);
		properties.setSegmentMaxBytes(1_000_000);
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "local-dev-key-01"), List.of("v1"));
		EncryptedWireAuditWal wal = new EncryptedWireAuditWal(properties, crypto);
		wal.initialize();
		assertThat(wal.append(WireAuditRecord.builder()
			.id("b".repeat(32))
			.stageCode("ADVERTISER_DELIVERY")
			.resultStatus("FAILED")
			.rawState("COMPLETE")
			.capturedAt(LocalDateTime.now())
			.build())).isTrue();
		WireAuditRepository unavailable = mock(WireAuditRepository.class);
		doThrow(new IllegalStateException("database unavailable")).when(unavailable).upsert(org.mockito.ArgumentMatchers.any());

		wal.rotateAndReplay(unavailable);

		try (var files = Files.list(directory)) {
			assertThat(files.map(path -> path.getFileName().toString()).toList()).anyMatch(name -> name.endsWith(".ready"))
				.noneMatch(name -> name.endsWith(".corrupt"));
		}
		WireAuditRepository recovered = mock(WireAuditRepository.class);
		wal.rotateAndReplay(recovered);
		verify(recovered, atLeastOnce()).upsert(org.mockito.ArgumentMatchers.any());
		try (var files = Files.list(directory)) {
			assertThat(files.map(path -> path.getFileName().toString()).toList()).noneMatch(name -> name.endsWith(".ready"));
		}
		wal.close();
	}

	@Test
	void unavailableWalDoesNotPreventStartupOrDatabaseFallback() throws Exception {
		Path unavailablePath = directory.resolve("not-a-directory");
		Files.writeString(unavailablePath, "occupied");
		WireAuditProperties properties = new WireAuditProperties();
		properties.setEnabled(true);
		properties.setWalPath(unavailablePath.toString());
		OcpxKeyRingCrypto crypto = new OcpxKeyRingCrypto("v1", Map.of("v1", "local-dev-key-01"), List.of("v1"));
		EncryptedWireAuditWal wal = new EncryptedWireAuditWal(properties, crypto);

		wal.initialize();

		assertThat(wal.append(WireAuditRecord.builder()
			.id("c".repeat(32))
			.stageCode("MEDIA_INGRESS")
			.resultStatus("PENDING")
			.rawState("PENDING")
			.capturedAt(LocalDateTime.now())
			.build())).isFalse();
		wal.close();
	}

}
