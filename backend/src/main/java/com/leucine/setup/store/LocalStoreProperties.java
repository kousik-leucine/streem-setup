package com.leucine.setup.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setup.store")
public record LocalStoreProperties(String path) {
}
