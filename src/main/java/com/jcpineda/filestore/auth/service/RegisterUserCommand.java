package com.jcpineda.filestore.auth.service;

public record RegisterUserCommand(String email, String password) {
}
