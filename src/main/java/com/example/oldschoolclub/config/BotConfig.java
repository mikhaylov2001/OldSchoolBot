package com.example.oldschoolclub.config;

import com.example.oldschoolclub.bot.OldSchoolBot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

    @Configuration
    @RequiredArgsConstructor
    public class BotConfig {

        private final OldSchoolBot oldSchoolBot;

        @Bean
        public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(oldSchoolBot);
            System.out.println("✅ Бот запущен!");
            return telegramBotsApi;
        }
    }
