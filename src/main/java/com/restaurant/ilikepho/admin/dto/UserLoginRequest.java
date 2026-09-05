package com.restaurant.ilikepho.admin.dto;

import com.restaurant.ilikepho.common.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {

    @NotBlank(message = MessageConstant.USERNAME_NOT_BLANK)
    private String userName;
    private String userPassword;
    private boolean rememberMe;

}
