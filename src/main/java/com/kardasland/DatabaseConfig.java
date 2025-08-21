package com.kardasland;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatabaseConfig {
	private final String type;
	private final String host;
	private final String port;
	private final String name;
	private final String user;
	private final String password;
	private final String filePath;
	private final boolean showSql;
}
