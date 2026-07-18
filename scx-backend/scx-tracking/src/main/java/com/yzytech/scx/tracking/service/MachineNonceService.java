package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.MachineNonce;
import com.yzytech.scx.tracking.mapper.MachineNonceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MachineNonceService {

	private final MachineNonceMapper mapper;

	@Transactional
	public boolean claim(String nonce) {
		if (nonce == null || nonce.length() < 8 || nonce.length() > 128)
			return false;
		mapper.deleteExpired();
		MachineNonce value = new MachineNonce();
		value.setNonce(nonce);
		value.setCreateTime(LocalDateTime.now());
		value.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		try {
			return mapper.insert(value) == 1;
		}
		catch (DuplicateKeyException exception) {
			return false;
		}
	}

}
