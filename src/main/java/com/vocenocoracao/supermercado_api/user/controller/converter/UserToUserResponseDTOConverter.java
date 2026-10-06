package com.vocenocoracao.supermercado_api.user.controller.converter;

import com.vocenocoracao.supermercado_api.user.dto.UserResponseDTO;
import com.vocenocoracao.supermercado_api.user.entity.User;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class UserToUserResponseDTOConverter implements Converter<User, UserResponseDTO> {

    @Override
    public UserResponseDTO convert(MappingContext<User, UserResponseDTO> context) {
        User user = context.getSource();

        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
