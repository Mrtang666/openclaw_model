package com.example.spring.cli.command.impl;

import com.example.spring.cli.command.core.Command;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MedicalIdentityWechatCommands {

    @Bean
    public Command patientWechatLoginCommand(WechatCommand wechatCommand) {
        return new IdentityWechatLoginCommand("patient", "微信接入：患者身份登录", "PATIENT", wechatCommand);
    }

    @Bean
    public Command caregiverWechatLoginCommand(WechatCommand wechatCommand) {
        return new IdentityWechatLoginCommand("caregiver", "微信接入：家属身份登录", "PARENT", wechatCommand);
    }

    @Bean
    public Command doctorWechatLoginCommand(WechatCommand wechatCommand) {
        return new IdentityWechatLoginCommand("doctor", "微信接入：医生身份登录", "DOCTOR", wechatCommand);
    }

    private record IdentityWechatLoginCommand(
            String name,
            String description,
            String requestedRole,
            WechatCommand wechatCommand) implements Command {

        @Override
        public String execute(List<String> arguments) {
            return wechatCommand.start(requestedRole);
        }
    }
}
