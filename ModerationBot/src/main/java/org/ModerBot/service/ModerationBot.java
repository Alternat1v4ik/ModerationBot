package org.ModerBot.service;

import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModerationBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ModerationBot.class);
    private final Map<String, ModeratorStatistics> stats = new HashMap<>();
    private static final String STATS_DIR = "stats";
    private static final List<String> allowedGroupNames = Arrays.asList(
            "MyGroup",
            "Вопросы | Mod. ML",
            "Отчеты | Mod. ML"
    );

    public ModerationBot() {
        createStatsDirectory();
        loadCurrentWeekStats();
    }

    @Override
    public String getBotUsername() {
        return "Вставьте имя бота";
    }

    @Override
    public String getBotToken() {
        return "Вставьте токен бота";
    }

    private void createStatsDirectory() {
        File dir = new File(STATS_DIR);
        if (!dir.exists() && dir.mkdirs()) {
            logger.info("Папка для статистики создана.");
        }
    }

    private void loadCurrentWeekStats() {
        String currentFileName = getCurrentStatsFileName();
        loadStats(currentFileName);
    }

    private String getCurrentStatsFileName() {
        LocalDate today = LocalDate.now();
        LocalDate sunday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
        return STATS_DIR + "/" + sunday.format(DateTimeFormatter.ofPattern("dd-MM")) + ".json";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat()) {
                notifyOwner(message);

                if (message.getChat().getTitle() != null && !isAllowedGroup(message.getChat().getTitle())) {
                    logger.info("Сообщение из неразрешённой группы: {}", message.getChat().getTitle());
                    return;
                }

                if (message.isCommand()) {
                    handleCommand(message);
                } else {
                    handleReport(message);
                }

            }
        }
    }

    private void notifyOwner(Message message) {
        String userName = message.getFrom().getUserName();
        if (userName == null) {
            userName = "неизвестный пользователь";
        }

        String groupName = message.getChat().getTitle();
        if (groupName == null) {
            groupName = "неизвестная группа";
        }

        if ("MyGroup".equals(groupName) || "Вопросы | Mod. ML".equals(groupName) || "Отчеты | Mod. ML".equals(groupName)) {
            logger.info("Бот добавлен в разрешенную группу: " + groupName + ".");
            return;
        }

        String notification = "@" + userName + " хотел добавить меня в группу: " + groupName;

        try {
            SendMessage privateMessage = SendMessage.builder()
                    .chatId(getOwnerChatId())
                    .text(notification)
                    .build();
            execute(privateMessage);
        } catch (Exception e) {
            logger.error("Ошибка при отправке личного сообщения владельцу: ", e);
        }
    }

    private String getOwnerChatId() {
        return "Вставьте свой айди";
    }

    private boolean isAllowedGroup(String groupName) {
        return allowedGroupNames.contains(groupName);
    }

    private void handleCommand(Message message) {
        String[] commandParts = message.getText().split(" ");
        String command = commandParts[0];
        String chatId = message.getChatId().toString();

        switch (command) {
            case "/getvio":
                if (isAdmin(message)) {
                    if (commandParts.length > 1) {
                        String requestedFileName = commandParts[1];
                        loadStats(STATS_DIR + "/" + requestedFileName + ".json");
                        sendWeeklyReport(message);
                    } else {
                        sendWeeklyReport(message);
                    }
                } else {
                    sendMessage(chatId, "Только главы могут использовать эту команду");
                }
                break;

            case "/statclear":
                if (commandParts.length > 1) {
                    String fileNameToDelete = STATS_DIR + "/" + commandParts[1] + ".json";
                    File file = new File(fileNameToDelete);
                    if (file.exists() && file.delete()) {
                        sendMessage(chatId, "Файл статистики удалён.");
                    } else {
                        sendMessage(chatId, "Файл статистики не найден.");
                    }
                }
                break;

            case "/getstats":
                if (isAdmin(message)) {
                    String adminChatId = message.getFrom().getId().toString();
                    sendFileList(adminChatId);
                    sendMessage(chatId, "Список файлов отправлен в лс.");
                } else {
                    sendMessage(chatId, "Только главы могут использовать эту команду");
                }
                break;
        }
    }

    private void sendFileList(String chatId) {
        File statsDir = new File(STATS_DIR);
        if (statsDir.exists() && statsDir.isDirectory()) {
            File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null && files.length > 0) {
                StringBuilder fileList = new StringBuilder("Доступные файлы статистики:\n");
                for (File file : files) {
                    fileList.append(file.getName()).append("\n");
                }
                sendMessage(chatId, fileList.toString());
            } else {
                sendMessage(chatId, "Файлы статистики отсутствуют.");
            }
        } else {
            sendMessage(chatId, "Папка статистики не найдена.");
        }
    }

    private boolean isAdmin(Message message) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(message.getChatId().toString());
            getChatMember.setUserId(message.getFrom().getId());

            org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember chatMember = execute(getChatMember);

            String status = chatMember.getStatus();
            return "administrator".equals(status) || "creator".equals(status);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при проверке прав администратора: ", e);
            return false;
        }
    }

    private void handleReport(Message message) {
        if (message.getForwardDate() != null) {
            return;
        }

        String text = message.getText();
        if (text == null || text.trim().isEmpty()) {
            text = message.getCaption();
        }

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String[] lines = text.split("\n");
        if (lines.length < 2) {
            return;
        }

        String[] firstLineParts = lines[0].split("-");
        if (firstLineParts.length < 2) {
            return;
        }

        String offenderName = firstLineParts[0].trim();
        String violationPoint = firstLineParts[1].trim();
        String secondLine = lines[1].trim();

        String violationType = null;

        switch (violationPoint) {
            case "3.1":
                if (secondLine.startsWith("https://www.youtube.com/") || secondLine.startsWith("https://youtu.be/") || secondLine.startsWith("https://drive.google.com/")) {
                    violationType = "Видео";
                } else {
                    return;
                }
                break;
            case "5.2":
            case "7.1":
                if (secondLine.startsWith("/warp")) {
                    violationType = "Warp";
                } else {
                    return;
                }
                break;
            case "6.1(неактив)":
            case "6.1":
            case "6.1(в бане)":
                if (secondLine.startsWith("/ad")) {
                    violationType = "Ad";
                } else {
                    return;
                }
                break;
            default:
                return;
        }

        String moderator = message.getFrom().getUserName();

        stats.putIfAbsent(moderator, new ModeratorStatistics(moderator));
        stats.get(moderator).addDetailedReport(offenderName, violationPoint, violationType, secondLine);
        saveStats();

        sendMessage(message.getChatId().toString(), "Отчёт принят");
    }

    private void sendWeeklyReport(Message message) {
        String userId = message.getFrom().getId().toString();
        StringBuilder report = new StringBuilder("Статистика за неделю:\n");

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        report.append("Период: с ").append(startOfWeek).append(" по ").append(endOfWeek).append("\n\n");

        boolean hasReports = false;

        for (ModeratorStatistics stat : stats.values()) {
            List<ModeratorStatistics.DetailedReport> weeklyReports = stat.getReportsBetween(startOfWeek, endOfWeek);

            if (weeklyReports.isEmpty()) {
                continue;
            }

            hasReports = true;

            int worldsDeleted = 0;
            int warpsDeletedFiveTwo = 0;
            int warpsDeletedSevenOne = 0;
            int cheatersBanned = 0;

            for (ModeratorStatistics.DetailedReport reportDetails : weeklyReports) {
                switch (reportDetails.getType()) {
                    case "Видео":
                        cheatersBanned++;
                        break;
                    case "Warp":
                        if ("5.2".equals(reportDetails.getPoint())) {
                            warpsDeletedFiveTwo++;
                        } else if ("7.1".equals(reportDetails.getPoint())) {
                            warpsDeletedSevenOne++;
                        }
                        break;
                    case "Ad":
                        worldsDeleted++;
                        break;
                }
            }

            report.append(stat.getModeratorName()).append(":\n")
                    .append("  Удаление миров - ").append(worldsDeleted).append(" шт.\n")
                    .append("  Удаление варпов (5.2) - ").append(warpsDeletedFiveTwo).append(" шт.\n")
                    .append("  Удаление варпов (7.1) - ").append(warpsDeletedSevenOne).append(" шт.\n")
                    .append("  Забаненых читеров - ").append(cheatersBanned).append(" шт.\n\n");
        }

        if (!hasReports) {
            sendMessage(userId, "За эту неделю отчётов нет.");
            return;
        }

        sendMessage(userId, report.toString());
        sendMessage(message.getChatId().toString(), "Статистика отправлена в личные сообщения.");
    }

    private void sendMessage(String chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }

    private void saveStats() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            String fileName = getCurrentStatsFileName();
            File file = new File(fileName);
            mapper.writeValue(file, stats);
            logger.info("Статистика сохранена в {}.", fileName);
        } catch (IOException e) {
            logger.error("Ошибка при сохранении статистики в файл.", e);
        }
    }

    private void loadStats(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            File file = new File(fileName);
            if (file.exists()) {
                Map<String, ModeratorStatistics> loadedStats = mapper.readValue(file, new TypeReference<Map<String, ModeratorStatistics>>() {});
                stats.clear();
                stats.putAll(loadedStats);
                logger.info("Статистика успешно загружена из файла {}.", fileName);
            } else {
                logger.info("Файл статистики {} не найден. Создаётся новая статистика.", fileName);
                stats.clear();
            }
        } catch (IOException e) {
            logger.error("Ошибка при загрузке статистики из файла.", e);
        }
    }
}


