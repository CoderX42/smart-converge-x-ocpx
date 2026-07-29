package com.yzytech.scx.subject.dto;

import lombok.Data;

/**
 * Minimal user projection exposed by media-scope assignment APIs.
 */
@Data
public class MediaScopeUserOption {

	private String id;

	private String username;

	private String nickname;

}
