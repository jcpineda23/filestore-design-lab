package com.jcpineda.filestore.auth.service;

public record LoginCommand(String email, String password) {
}
