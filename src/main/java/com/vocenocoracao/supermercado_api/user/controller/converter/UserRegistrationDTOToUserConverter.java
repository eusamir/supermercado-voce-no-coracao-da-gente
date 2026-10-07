package com.vocenocoracao.supermercado_api.user.controller.converter;

import com.vocenocoracao.supermercado_api.user.dto.UserRegistrationDTO;
import com.vocenocoracao.supermercado_api.user.entity.User;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class UserRegistrationDTOToUserConverter implements Converter<UserRegistrationDTO, User> {

    @Override
    public User convert(MappingContext<UserRegistrationDTO, User> context) {
        UserRegistrationDTO userRegistrationDTO = context.getSource();

        User user = new User();
        user.setName(userRegistrationDTO.name().trim().replaceAll("\\s+", " "));
        user.setEmail(userRegistrationDTO.email().trim().toLowerCase());
        return user;
    }
}
