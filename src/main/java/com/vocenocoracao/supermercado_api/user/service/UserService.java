package com.vocenocoracao.supermercado_api.user.service;

import com.vocenocoracao.supermercado_api.user.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    User register(User user, String password);

    User getCurrentUser(Jwt jwt);
}
