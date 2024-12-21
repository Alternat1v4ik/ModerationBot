package org.ModerBot;

import org.ModerBot.service.ModerationBot;
import org.ModerBot.service.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Эта хрень для запуска ботов
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Регистрируете первого бота
            botsApi.registerBot(new ModerationBot());

            // Регистрируете второго бота
            botsApi.registerBot(new TelegramBot());

            System.out.println("Оба бота успешно запущены!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}