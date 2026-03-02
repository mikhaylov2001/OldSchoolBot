package com.example.oldschoolclub.bot;

import com.example.oldschoolclub.model.Booking;
import com.example.oldschoolclub.model.Client;
import com.example.oldschoolclub.model.Zone;
import com.example.oldschoolclub.repository.BookingRepository;
import com.example.oldschoolclub.repository.ClientRepository;
import com.example.oldschoolclub.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OldSchoolBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final Long adminId;
    private final ClientRepository clientRepository;
    private final ZoneRepository zoneRepository;
    private final BookingRepository bookingRepository;

    private final Map<Long, BotState> userStates = new HashMap<>();
    private final Map<Long, Booking> tempBookings = new HashMap<>();

    private final Map<Long, Integer> dateOffsets = new HashMap<>();
    private final Map<Long, Integer> adminDateOffsets = new HashMap<>();

    private static final int DATE_WINDOW_DAYS = 7;
    private static final int DATE_MAX_FORWARD_DAYS = 60;

    private static final Locale RU = new Locale("ru");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM (EEE)", RU);
    private static final DateTimeFormatter DATE_FMT_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy", RU);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", RU);

    @Autowired
    public OldSchoolBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.admin.id}") Long adminId,
            ClientRepository clientRepository,
            ZoneRepository zoneRepository,
            BookingRepository bookingRepository
    ) {
        super(botToken);
        this.botUsername = botUsername;
        this.adminId = adminId;
        this.clientRepository = clientRepository;
        this.zoneRepository = zoneRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            handleMessage(chatId, text, update);
        } else if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            handleCallback(chatId, data, update);
        }
    }

    // ===================== MESSAGE =====================

    private void handleMessage(long chatId, String text, Update update) {
        BotState state = userStates.getOrDefault(chatId, BotState.START);

        if ("/start".equals(text)) {
            showMainMenu(chatId, update.getMessage().getFrom().getFirstName(), null);
            return;
        }

        if ("/admin".equalsIgnoreCase(text) && isAdmin(chatId)) {
            showAdminMenu(chatId, null);
            return;
        }

        if ("/mybookings".equalsIgnoreCase(text)) {
            showMyBookings(chatId, null);
            return;
        }

        switch (state) {
            case ENTER_NAME -> handleNameInput(chatId, text);
            case ENTER_PHONE -> handlePhoneInput(chatId, text);
            default -> showMainMenu(chatId, "", null);
        }
    }

    // ===================== CALLBACK =====================

    private void handleCallback(long chatId, String data, Update update) {
        ackCallback(update);

        if ("home".equals(data)) {
            showMainMenu(chatId, "", update);
            return;
        }

        // paging ДО date...
        if (data.startsWith("datepage:")) {
            handleDatePage(chatId, data, update);
            return;
        }
        if (data.startsWith("adminpage:")) {
            if (isAdmin(chatId)) handleAdminPage(chatId, data, update);
            return;
        }
        if (data.startsWith("adminday:")) {
            if (isAdmin(chatId)) handleAdminDay(chatId, data, update);
            return;
        }

        if ("newbooking".equals(data)) showZones(chatId, update);
        else if ("mybookings".equals(data)) showMyBookings(chatId, update);
        else if ("confirmno".equals(data)) showMainMenu(chatId, "", update);

        else if (data.startsWith("zone")) handleZoneChoice(chatId, data, update);
        else if (data.startsWith("date")) handleDateChoice(chatId, data, update);
        else if (data.startsWith("time")) handleTimeChoice(chatId, data, update);
        else if (data.startsWith("duration")) handleDurationChoice(chatId, data, update);

        else if ("confirmyes".equals(data)) confirmBooking(chatId);

        else if (data.startsWith("cancelbooking")) cancelBooking(chatId, data, update);

            // admin
        else if ("admin_menu".equals(data) && isAdmin(chatId)) showAdminMenu(chatId, update);
        else if ("admin_allbookings".equals(data) && isAdmin(chatId)) showAdminAllBookings(chatId, update);
        else if ("admin_pick_date".equals(data) && isAdmin(chatId)) showAdminDatePicker(chatId, update);
    }

    // ===================== MAIN MENU =====================

    private void showMainMenu(long chatId, String name, Update update) {
        userStates.put(chatId, BotState.START);

        String greeting = (name == null || name.isEmpty()) ? "👋 Привет!" : "👋 Привет, " + name + "!";

        List<Zone> zones = zoneRepository.findByActiveTrue();
        StringBuilder prices = new StringBuilder();
        for (Zone z : zones) {
            String icon = zoneIcon(z.getName());
            String title = cleanZoneName(z.getName());
            prices.append("• ").append(icon).append(" ").append(title)
                    .append(" — ").append(z.getPricePerHour().intValue()).append("₽/ч\n");
        }
        if (prices.isEmpty()) prices.append("Пока нет активных зон.\n");

        String text =
                greeting +
                        "\n\n🎮 *Old School Club* — уютный игровой клуб, где можно круто провести время." +
                        "\nВыбирай зону, бронируй удобные дату и время — и приходи играть." +
                        "\n\n💰 *Цены:*\n" + prices +
                        "\n📅 Жми «Забронировать» и выбирай удобный слот.";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🎮 Забронировать", "newbooking")));
        rows.add(List.of(btn("📋 Мои брони", "mybookings")));
        if (isAdmin(chatId)) rows.add(List.of(btn("🛠 Админ", "admin_menu")));
        markup.setKeyboard(rows);

        sendOrEdit(update, chatId, text, markup);
    }

    // ===================== ZONES =====================

    private void showZones(long chatId, Update update) {
        userStates.put(chatId, BotState.CHOOSE_ZONE);

        List<Zone> zones = zoneRepository.findByActiveTrue();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Zone zone : zones) {
            String icon = zoneIcon(zone.getName());
            String title = cleanZoneName(zone.getName());
            String label = icon + " " + title + " — " + zone.getPricePerHour().intValue() + "₽/ч";
            rows.add(List.of(btn(label, "zone" + zone.getId())));
        }

        rows.add(List.of(btn("🏠 Главная", "home")));
        markup.setKeyboard(rows);

        sendOrEdit(update, chatId, "📍 Выбери зону:", markup);
    }

    private void handleZoneChoice(long chatId, String data, Update update) {
        Long zoneId = Long.parseLong(data.replace("zone", ""));
        Zone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null) return;

        Booking booking = new Booking();
        booking.setZone(zone);
        tempBookings.put(chatId, booking);

        userStates.put(chatId, BotState.CHOOSE_DATE);
        dateOffsets.put(chatId, 0);

        showDatePicker(chatId, update);
    }

    // ===================== DATE PICKER (USER) =====================

    private void showDatePicker(long chatId, Update update) {
        int offset = dateOffsets.getOrDefault(chatId, 0);
        offset = Math.max(0, Math.min(offset, DATE_MAX_FORWARD_DAYS));
        dateOffsets.put(chatId, offset);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        LocalDate base = LocalDate.now().plusDays(offset);

        for (int i = 0; i < DATE_WINDOW_DAYS; i++) {
            LocalDate date = base.plusDays(i);
            String label;
            if (offset == 0 && i == 0) label = "📅 Сегодня";
            else if (offset == 0 && i == 1) label = "📅 Завтра";
            else label = "📅 " + date.format(DATE_FMT);

            rows.add(List.of(btn(label, "date" + date)));
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("◀️", "datepage:" + Math.max(0, offset - DATE_WINDOW_DAYS)));
        nav.add(btn("▶️", "datepage:" + Math.min(DATE_MAX_FORWARD_DAYS, offset + DATE_WINDOW_DAYS)));
        rows.add(nav);

        rows.add(List.of(btn("⬅️ Назад (зоны)", "newbooking")));
        rows.add(List.of(btn("🏠 Главная", "home")));

        markup.setKeyboard(rows);
        sendOrEdit(update, chatId, "📅 Выбери дату (можно листать):", markup);
    }

    private void handleDatePage(long chatId, String data, Update update) {
        int newOffset = Integer.parseInt(data.replace("datepage:", ""));
        dateOffsets.put(chatId, newOffset);
        showDatePicker(chatId, update);
    }

    // ===================== TIME PICKER =====================

    private void handleDateChoice(long chatId, String data, Update update) {
        LocalDate date = LocalDate.parse(data.replace("date", ""));

        Booking b = tempBookings.get(chatId);
        if (b == null || b.getZone() == null) {
            showZones(chatId, update);
            return;
        }

        b.setDate(date);
        userStates.put(chatId, BotState.CHOOSE_TIME);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        Long zoneId = b.getZone().getId();

        for (int hour = 10; hour <= 21; hour++) {
            LocalTime time = LocalTime.of(hour, 0);

            boolean busy = bookingRepository.existsByZoneIdAndDateAndStartTimeBetween(
                    zoneId, date,
                    time.minusMinutes(59),
                    time.plusMinutes(59)
            );

            if (!busy) {
                row.add(btn(String.format("%02d:00", hour), "time" + time));
                if (row.size() == 3) {
                    rows.add(new ArrayList<>(row));
                    row.clear();
                }
            }
        }
        if (!row.isEmpty()) rows.add(row);

        rows.add(List.of(btn("⬅️ Назад (даты)", "datepage:" + dateOffsets.getOrDefault(chatId, 0))));
        rows.add(List.of(btn("🏠 Главная", "home")));

        markup.setKeyboard(rows);
        sendOrEdit(update, chatId, "⏰ Выбери время:", markup);
    }

    // ===================== DURATION =====================

    private void handleTimeChoice(long chatId, String data, Update update) {
        LocalTime time = LocalTime.parse(data.replace("time", ""));

        Booking b = tempBookings.get(chatId);
        if (b == null) return;

        b.setStartTime(time);
        userStates.put(chatId, BotState.CHOOSE_DURATION);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(btn("1 час", "duration1"), btn("2 часа", "duration2"), btn("3 часа", "duration3")),
                List.of(btn("⬅️ Назад (время)", "date" + b.getDate())),
                List.of(btn("🏠 Главная", "home"))
        ));

        sendOrEdit(update, chatId, "⏳ На сколько часов?", markup);
    }

    private void handleDurationChoice(long chatId, String data, Update update) {
        int duration = Integer.parseInt(data.replace("duration", ""));

        Booking b = tempBookings.get(chatId);
        if (b == null) return;

        b.setDurationHours(duration);
        userStates.put(chatId, BotState.ENTER_NAME);

        send(chatId, "✍️ Введи имя:", null);
    }

    // ===================== CONTACTS =====================

    private void handleNameInput(long chatId, String name) {
        Client client = clientRepository.findByTelegramId(chatId).orElse(new Client());
        client.setTelegramId(chatId);
        client.setFirstName(name);
        clientRepository.save(client);

        userStates.put(chatId, BotState.ENTER_PHONE);
        send(chatId, "📞 Введи номер телефона:", null);
    }

    private void handlePhoneInput(long chatId, String phone) {
        Client client = clientRepository.findByTelegramId(chatId).orElse(null);
        if (client == null) return;

        client.setPhone(phone);
        clientRepository.save(client);

        Booking b = tempBookings.get(chatId);
        if (b == null) return;
        b.setClient(client);

        userStates.put(chatId, BotState.CONFIRM_BOOKING);

        String summary =
                "✅ *Проверь бронь:*\n\n" +
                        "🎮 Зона: " + cleanZoneName(b.getZone().getName()) + "\n" +
                        "📅 Дата: " + b.getDate().format(DATE_FMT_FULL) + "\n" +
                        "⏰ Время: " + b.getStartTime().format(TIME_FMT) + "\n" +
                        "⏳ Длительность: " + b.getDurationHours() + " ч\n" +
                        "💰 Цена: " + (b.getZone().getPricePerHour().intValue() * b.getDurationHours()) + "₽\n\n" +
                        "👤 Имя: " + client.getFirstName() + "\n" +
                        "📞 Телефон: " + client.getPhone();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(btn("✅ Подтвердить", "confirmyes"), btn("❌ Отмена", "confirmno")),
                List.of(btn("🏠 Главная", "home"))
        ));

        send(chatId, summary, markup);
    }

    private void confirmBooking(long chatId) {
        Booking booking = tempBookings.get(chatId);
        if (booking == null) return;

        bookingRepository.save(booking);
        tempBookings.remove(chatId);
        userStates.put(chatId, BotState.START);

        send(chatId, "✅ Готово! Бронь создана.", null);

        String adminMsg =
                "📌 *Новая бронь!*\n\n" +
                        "👤 " + booking.getClient().getFirstName() + "\n" +
                        "📞 " + booking.getClient().getPhone() + "\n" +
                        "🎮 " + cleanZoneName(booking.getZone().getName()) + "\n" +
                        "📅 " + booking.getDate().format(DATE_FMT_FULL) + "\n" +
                        "⏰ " + booking.getStartTime().format(TIME_FMT) + "\n" +
                        "⏳ " + booking.getDurationHours() + " ч";

        send(adminId, adminMsg, null);
    }

    // ===================== MY BOOKINGS + CANCEL =====================

    private void showMyBookings(long chatId, Update update) {
        Client client = clientRepository.findByTelegramId(chatId).orElse(null);
        if (client == null) {
            sendOrEdit(update, chatId, "ℹ️ Ты ещё не зарегистрирован. Нажми /start", null);
            return;
        }

        List<Booking> bookings = bookingRepository.findByClientAndStatus(client, Booking.BookingStatus.ACTIVE);
        if (bookings.isEmpty()) {
            sendOrEdit(update, chatId, "ℹ️ У тебя пока нет активных броней. Создай первую! 🎮", null);
            return;
        }

        bookings.sort(Comparator.comparing(Booking::getDate).thenComparing(Booking::getStartTime));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder sb = new StringBuilder("📋 *Твои активные брони:*\n\n");

        for (Booking b : bookings) {
            sb.append("• ").append(cleanZoneName(b.getZone().getName()))
                    .append(" — ").append(b.getDate().format(DATE_FMT_FULL))
                    .append(" ").append(b.getStartTime().format(TIME_FMT))
                    .append(" (").append(b.getDurationHours()).append("ч)\n");

            rows.add(List.of(btn("❌ Отменить " + b.getDate().format(DateTimeFormatter.ofPattern("dd.MM", RU)) +
                    " " + b.getStartTime().format(TIME_FMT), "cancelbooking" + b.getId())));
        }

        rows.add(List.of(btn("🏠 Главная", "home")));
        markup.setKeyboard(rows);

        sendOrEdit(update, chatId, sb.toString(), markup);
    }

    private void cancelBooking(long chatId, String data, Update update) {
        long bookingId = Long.parseLong(data.replace("cancelbooking", ""));
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            sendOrEdit(update, chatId, "Не нашёл бронь.", null);
            return;
        }

        Client client = clientRepository.findByTelegramId(chatId).orElse(null);
        if (client == null || booking.getClient() == null ||
                !Objects.equals(booking.getClient().getTelegramId(), client.getTelegramId())) {
            sendOrEdit(update, chatId, "⛔ Нельзя отменить чужую бронь.", null);
            return;
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        sendOrEdit(update, chatId, "✅ Бронь отменена.", homeOnlyKb());

        String adminMsg =
                "❌ *Отмена брони*\n\n" +
                        "👤 " + (booking.getClient() != null ? booking.getClient().getFirstName() : "—") + "\n" +
                        "📞 " + (booking.getClient() != null ? booking.getClient().getPhone() : "—") + "\n" +
                        "🎮 " + cleanZoneName(booking.getZone().getName()) + "\n" +
                        "📅 " + booking.getDate().format(DATE_FMT_FULL) + "\n" +
                        "⏰ " + booking.getStartTime().format(TIME_FMT);

        send(adminId, adminMsg, null);
    }

    // ===================== ADMIN =====================

    private void showAdminMenu(long chatId, Update update) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(btn("📊 Все записи", "admin_allbookings")),
                List.of(btn("📅 Выбрать дату", "admin_pick_date")),
                List.of(btn("🏠 Главная", "home"))
        ));
        sendOrEdit(update, chatId, "🛠 *Админ меню*", markup);
    }

    private void showAdminAllBookings(long chatId, Update update) {
        int offset = adminDateOffsets.getOrDefault(chatId, 0);
        offset = Math.max(0, Math.min(offset, DATE_MAX_FORWARD_DAYS));
        adminDateOffsets.put(chatId, offset);

        LocalDate day = LocalDate.now().plusDays(offset);

        // показываем только ACTIVE, чтобы отменённые исчезали
        List<Booking> bookings = bookingRepository.findByDateAndStatus(day, Booking.BookingStatus.ACTIVE);
        bookings.sort(Comparator.comparing(Booking::getStartTime));

        StringBuilder sb = new StringBuilder("📊 *Все активные записи на* " + day.format(DATE_FMT_FULL) + "\n\n");
        if (bookings.isEmpty()) {
            sb.append("Нет записей.");
        } else {
            for (Booking b : bookings) {
                sb.append("⏰ ").append(b.getStartTime().format(TIME_FMT))
                        .append(" (").append(b.getDurationHours()).append("ч)")
                        .append(" — ").append(cleanZoneName(b.getZone().getName()))
                        .append("\n👤 ").append(b.getClient() != null ? b.getClient().getFirstName() : "—")
                        .append(" / ").append(b.getClient() != null ? b.getClient().getPhone() : "—")
                        .append("\n\n");
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("◀️", "adminpage:" + Math.max(0, offset - 1)));
        nav.add(btn("▶️", "adminpage:" + Math.min(DATE_MAX_FORWARD_DAYS, offset + 1)));
        rows.add(nav);

        rows.add(List.of(btn("📅 Выбрать дату", "admin_pick_date")));
        rows.add(List.of(btn("🏠 Главная", "home")));

        markup.setKeyboard(rows);
        sendOrEdit(update, chatId, sb.toString(), markup);
    }

    private void showAdminDatePicker(long chatId, Update update) {
        int offset = adminDateOffsets.getOrDefault(chatId, 0);
        offset = Math.max(0, Math.min(offset, DATE_MAX_FORWARD_DAYS));
        adminDateOffsets.put(chatId, offset);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        LocalDate base = LocalDate.now().plusDays(offset);

        for (int i = 0; i < DATE_WINDOW_DAYS; i++) {
            LocalDate d = base.plusDays(i);
            rows.add(List.of(btn("📅 " + d.format(DATE_FMT), "adminday:" + (offset + i))));
        }

        rows.add(List.of(
                btn("◀️", "adminpage:" + Math.max(0, offset - DATE_WINDOW_DAYS)),
                btn("▶️", "adminpage:" + Math.min(DATE_MAX_FORWARD_DAYS, offset + DATE_WINDOW_DAYS))
        ));

        rows.add(List.of(btn("🏠 Главная", "home")));
        markup.setKeyboard(rows);

        sendOrEdit(update, chatId, "📅 Выбери дату для просмотра:", markup);
    }

    private void handleAdminDay(long chatId, String data, Update update) {
        int offset = Integer.parseInt(data.replace("adminday:", ""));
        adminDateOffsets.put(chatId, offset);
        showAdminAllBookings(chatId, update);
    }

    private void handleAdminPage(long chatId, String data, Update update) {
        int newOffset = Integer.parseInt(data.replace("adminpage:", ""));
        adminDateOffsets.put(chatId, newOffset);
        showAdminDatePicker(chatId, update);
    }

    // ===================== HELPERS =====================

    private boolean isAdmin(long chatId) {
        return Objects.equals(chatId, adminId) || chatId == 6612275923L;
    }

    private String cleanZoneName(String name) {
        if (name == null) return "";
        return name
                .replace("🪑", "")
                .replace("🖥", "")
                .replace("🖥︎", "")
                .replace("🖥️", "")
                .replace("🎮", "")
                .trim();
    }

    private String zoneIcon(String zoneName) {
        String n = zoneName == null ? "" : zoneName.toLowerCase();
        // если содержит "ps" -> джойстик, иначе -> комп (текстовый, "старый")
        return n.contains("ps") ? "🎮" : "🖥︎";
    }

    private InlineKeyboardMarkup homeOnlyKb() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("🏠 Главная", "home"))));
        return m;
    }

    private InlineKeyboardButton btn(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private void ackCallback(Update update) {
        try {
            if (update != null && update.hasCallbackQuery()) {
                execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendOrEdit(Update update, long chatId, String text, InlineKeyboardMarkup markup) {
        if (update != null && update.hasCallbackQuery()) {
            Message m = update.getCallbackQuery().getMessage();

            boolean sameText = Objects.equals(m.getText(), text);
            boolean sameMarkup = Objects.equals(m.getReplyMarkup(), markup);
            if (sameText && sameMarkup) return;

            EditMessageText edit = new EditMessageText();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(m.getMessageId());
            edit.setText(text);
            edit.setParseMode("Markdown");
            if (markup != null) edit.setReplyMarkup(markup);

            try {
                execute(edit);
            } catch (TelegramApiException e) {
                if (e.getMessage() != null && e.getMessage().contains("message is not modified")) return;
                e.printStackTrace();
            }
        } else {
            send(chatId, text, markup);
        }
    }

    private void send(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setParseMode("Markdown");
        if (markup != null) msg.setReplyMarkup(markup);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
