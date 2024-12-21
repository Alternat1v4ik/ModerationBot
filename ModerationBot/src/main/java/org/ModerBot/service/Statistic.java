package org.ModerBot.service;

public class Statistic {
    private Logs fullogs;
    private Logs mhistory;

    public Statistic(String fullogs, String mhistory) {
        this.fullogs = new Logs(fullogs);
        this.mhistory = new Logs(mhistory);
        collectStatistic(false);
    }

    private void swap() {
        Logs temp = fullogs;
        fullogs = mhistory;
        mhistory = temp;
    }

    private void collectStatistic(boolean isRepeatedCall) {
        String nickname = fullogs.getNickname();
        int reports = fullogs.countOfReports();
        int warns = fullogs.countOfWarns();
        int bans = mhistory.countOfBans();
        int mutes = mhistory.countOfMutes();
        String unbannedPlayers = fullogs.findPlayersWithRemovedPunish("/unban ");
        String unmutedPlayers = fullogs.findPlayersWithRemovedPunish("/unmute ");
        if (nickname.isEmpty()) {
            TelegramBot.sendMessage("Страница пуста и/или указан некорректный адрес.\n");
        } else if (reports == 0 && warns == 0 && bans == 0 && mutes == 0 && !isRepeatedCall) {
            swap();
            collectStatistic(true);
        } else {
            sendStatistic(nickname, reports, mutes, bans, warns, unbannedPlayers, unmutedPlayers);
        }
    }

    private void sendStatistic(String nickname, int reports, int mutes, int bans, int warns, String unbannedPlayers, String unmutedPlayers) {
        TelegramBot.sendMessage("Statistic " + nickname + "\n"+"\n" +
                (reports != 0 ? "Reports: " + reports + "\n" : "Нет разобранных репортов\n") +
                (mutes != 0 ? "Mutes: " + mutes + "\n" : "Нет выданных мутов\n") +
                (bans != 0 ? "Bans: " + bans + "\n" : "Нет выданных банов\n") +
                (warns != 0 ? "Answer to: " + warns + "\n" : "Нет выданных варнов\n") +
                (!unbannedPlayers.isEmpty() ? "\nList unbanned players:\n" + unbannedPlayers : "") +
                (!unmutedPlayers.isEmpty() ? "\nList unmutted players:\n" + unmutedPlayers : ""));
    }
}
