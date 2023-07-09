package ru.andver00.Controler;

import lombok.extern.log4j.Log4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.andver00.Entity.HotelRoom;
import ru.andver00.Enum.UserState;
import ru.andver00.Service.IAppUserService;
import ru.andver00.Service.IMainService;
import com.vdurmont.emoji.EmojiParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Log4j
public class BotController extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Value("${bot.token}")
    private String botToken;
    @Value("${owner.username}")
    private String adminUsername;

    boolean accessModifier = false;

    private final IMainService mainService;
    private final IAppUserService appUserService;

    private HotelRoom currentRoom;

    static final String HELP_TEXT = EmojiParser.parseToUnicode("""
                    You can control me by sending these commands:
            
                    :question: /help  - to get info about all other commands
                    
                    :books: /info  - to get info about our hotels
                    
                    :bed: /rooms  - to get info about rooms that you can book
                    """);

    static final String START_TEXT = EmojiParser.parseToUnicode("""
                    Hello, dear customer! :smile:

                    I am created to simulate booking process of the hotel room. You can use menu buttons to control me.
                    """);

    static final String INFO_TEXT = EmojiParser.parseToUnicode(
                    "1) <b> L'Empereur Hotel </b>\n" +
                    "Our hotel is a great place to relax and have a good time. " +
                    "It is located in the heart of the city and has everything you need for a comfortable stay. " +
                    "Our hotel has a grand and luxurious establishment, with a grand entrance and a beautiful lobby. " +
                    "All this makes our hotel popular destination for travelers, politicians and etc. \n\n" +
                            "2) <b>The Grand Royale </b> \n" +
                            "This hotel is designed to embody the grandeur and luxury associated with the 19th century, while incorporating modern amenities and techniques." +
                            "The hotel's rooms and suites are designed with a classic and timeless look, with attention paid to every detail. High-quality furnishings, such as antique chairs and tables, are combined with modern elements, such as flat-screen televisions and high-speed internet." +
                            "The hotel's restaurant, as well as its bar, are both designed with a sense of sophistication and style. "
    );

    public BotController(IMainService mainService, IAppUserService appUserService) {
        this.mainService = mainService;
        this.appUserService = appUserService;
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            this.accessModifier = update.getMessage().getChat().getUserName().equals(adminUsername);
            var message = update.getMessage();
            var chatId = message.getChatId().toString();
            User user = message.getFrom();
            String currentState = mainService.getCurrentUserState(user);

            if (currentState == null) {
                startCommand(message);
                return;
            }

            switch (currentState) {
                case "BASIC_STATE":
                    if (message.hasText()) {
                        switch (message.getText()) {
                            case "/start" -> startCommand(message);
                            case "/help" -> helpCommand(message);
                            case "/info" -> infoCommand(message);
                            case "/rooms" -> roomsCommand(message);
                            case "/addNewRoom" -> addNewRoomCommand(message);
                        }
                    }
                    break;

                case "WAIT_FOR_ROOM_NUMBER":
                case "WAIT_FOR_NUMBER_OF_ROOMS":
                case "WAIT_FOR_DESCRIPTION":
                case "WAIT_FOR_PHOTO":
                    addNewRoomCommand(message);
                    break;

                case "VIEWING_ROOMS_LIST":
                    sendTextMessage(chatId, "Before using other commands end your viewing session!");
                    ;
                    break;

                default:
                    sendTextMessage(chatId, EmojiParser.parseToUnicode("Cannot define your message! Try again pls :smile:"));
                    break;
            }

            log.debug(message.getChat().getUserName() + ": " + message.getText());

        }
        else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            var message = update.getCallbackQuery().getMessage();
            var chatId = update.getCallbackQuery().getMessage().getChatId().toString();

            switch (callbackData) {
                case "PREV_ROOM" -> {
                    User user = update.getCallbackQuery().getFrom();
                    mainService.setRoomForAppUser(user, -1L);
                    roomsCommandEdit(message, user, chatId);
                }
                case "NEXT_ROOM" -> {
                    User user = update.getCallbackQuery().getFrom();
                    mainService.setRoomForAppUser(user, 1L);
                    roomsCommandEdit(message, user, chatId);
                }
                case "CANCEL_LIST_VIEW" -> {
                    User user = update.getCallbackQuery().getFrom();
                    mainService.setCurrentUserState(user, UserState.BASIC_STATE);
                    try {
                        this.execute(new DeleteMessage(chatId, message.getMessageId()));
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
                case "BOOK" -> {
                    User user = update.getCallbackQuery().getFrom();
                    try {
                        this.execute(SendInvoice.builder()
                                .chatId(chatId)
                                .currency("RUB")
                                .providerToken("401643678:TEST:9cedc4b0-a481-427b-a3a9-b7f775725107")
                                .title("Test payment")
                                .description("Pay for hotel room booking ")
                                .payload("Test payment")
                                .price(new LabeledPrice("Test",10000))
                                .startParameter(message.getMessageId().toString())
                                .build());
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void startCommand(Message message) {
        User user = message.getFrom();
        var appUser = mainService.findOrSaveAppUser(user);
        sendTextMessage(message.getChatId().toString(), START_TEXT);
    }

    public void helpCommand(Message message) {
        sendTextMessage(message.getChatId().toString(), HELP_TEXT);
    }

    private void infoCommand(Message message) {
        sendPhotoMessage(message.getChatId().toString(), INFO_TEXT, "bit.ly/413FUaP");
    }

    private InlineKeyboardMarkup getButtonsForRoomsList(Long currentRoomId) {
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> firstRowInLine = new ArrayList<>();

        var prevButton = new InlineKeyboardButton();
        var nextButton = new InlineKeyboardButton();
        var bookButton = new InlineKeyboardButton();

        prevButton.setText("Previous");
        nextButton.setText("Next");
        bookButton.setText("Book");
        prevButton.setCallbackData("PREV_ROOM");
        nextButton.setCallbackData("NEXT_ROOM");
        bookButton.setCallbackData("BOOK");


        if (currentRoomId.equals(1L)) {
            firstRowInLine.add(bookButton);
            firstRowInLine.add(nextButton);
        }
        else if (mainService.getHotelRoomById(currentRoomId + 1) == null) {
            firstRowInLine.add(prevButton);
            firstRowInLine.add(bookButton);
        }
        else {
            firstRowInLine.add(prevButton);
            firstRowInLine.add(bookButton);
            firstRowInLine.add(nextButton);
        }

        rowsInLine.add(firstRowInLine);

        List<InlineKeyboardButton> secondRowInLine = new ArrayList<>();

        var cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Cancel");
        cancelButton.setCallbackData("CANCEL_LIST_VIEW");
        secondRowInLine.add(cancelButton);
        rowsInLine.add(secondRowInLine);

        markupInLine.setKeyboard(rowsInLine);

        return  markupInLine;
    }

    private void roomsCommand(Message message) {
        User user = message.getFrom();
        mainService.setCurrentUserState(user, UserState.VIEWING_ROOMS_LIST);

        var appUser = mainService.findOrSaveAppUser(user);

        var currentRoomId = appUser.getCurrentRoomInListId();

        HotelRoom room = mainService.getHotelRoomById(currentRoomId);

        String text =
                "Room info:\n" +
                        "       Room number: " + room.getNumber().toString() + "\n" +
                        "       Number of rooms inside: " + room.getNumberOfRooms().toString() + "\n" +
                        "       Description: " + room.getDescription();
        String link = room.getPhoto();

        InlineKeyboardMarkup markupInLine = getButtonsForRoomsList(currentRoomId);

        var response = new SendPhoto();
        var photo = new InputFile(link);

        response.setChatId(message.getChatId().toString());
        response.setPhoto(photo);
        response.setCaption(text);
        response.setReplyMarkup(markupInLine);

        try {
            this.execute(response);
        } catch (TelegramApiException e) {
            log.error(e);
        }
    }

    private void roomsCommandEdit(Message message, User user, String chatId) {
        var appUser = mainService.findOrSaveAppUser(user);
        var currentRoomId = appUser.getCurrentRoomInListId();


        var editMessageText = new EditMessageCaption();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(message.getMessageId());

        HotelRoom room = mainService.getHotelRoomById(currentRoomId);

        String text =
                "Room info:\n" +
                        "       <b>Room number</b>: " + room.getNumber().toString() + "\n" +
                        "       <b>Hotel</b>:  L'Empereur Hotel" + "\n" +
                        "       <b>Number of rooms inside</b>: " + room.getNumberOfRooms().toString() + "\n" +
                        "       <b>Description</b>: " + room.getDescription();

        editMessageText.setParseMode(ParseMode.HTML);
        editMessageText.setCaption(text);

        var editMessageButtons = new EditMessageReplyMarkup();
        InlineKeyboardMarkup markupInLine = getButtonsForRoomsList(currentRoomId);
        editMessageButtons.setChatId(chatId);
        editMessageButtons.setMessageId(message.getMessageId());
        editMessageButtons.setReplyMarkup(markupInLine);

        var editMessagePhoto = new EditMessageMedia();
        editMessagePhoto.setChatId(chatId);
        editMessagePhoto.setMessageId(message.getMessageId());
        editMessagePhoto.setMedia(new InputMediaPhoto(room.getPhoto()));

        try {
            this.execute(editMessagePhoto);
            this.execute(editMessageText);
            this.execute(editMessageButtons);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void bookingCommand(Message message) {

    }

    private void clearCommand(Message message) {
        try {
            this.execute(new DeleteMessage(message.getChatId().toString(), message.getMessageId()));
        } catch (TelegramApiException e) {
            log.error(e);
        }
    }

    private void addNewRoomCommand(Message message) {
        var chatId = message.getChatId().toString();
        User user = message.getFrom();
        String currentState = mainService.getCurrentUserState(user);

        if (this.accessModifier) {
            switch (currentState) {
                case "BASIC_STATE" -> {
                    sendTextMessage(chatId, "Your access level is enough to use this command!");
                    sendTextMessage(chatId, "Send a number of a new room:");
                    currentRoom = new HotelRoom();
                    mainService.setCurrentUserState(user, UserState.WAIT_FOR_ROOM_NUMBER);
                }
                case "WAIT_FOR_ROOM_NUMBER" -> {
                    var roomNumber = Long.valueOf(message.getText());
                    currentRoom.setNumber(roomNumber);
                    sendTextMessage(chatId, "Send a number of rooms in this room:");
                    mainService.setCurrentUserState(user, UserState.WAIT_FOR_NUMBER_OF_ROOMS);
                }
                case "WAIT_FOR_NUMBER_OF_ROOMS" -> {
                    var roomsNumber = Long.valueOf(message.getText());
                    currentRoom.setNumberOfRooms(roomsNumber);
                    sendTextMessage(chatId, "Send a description for this room:");
                    mainService.setCurrentUserState(user, UserState.WAIT_FOR_DESCRIPTION);
                }
                case "WAIT_FOR_DESCRIPTION" -> {
                    var description = message.getText();
                    currentRoom.setDescription(description);
                    sendTextMessage(chatId, "Send a photo URL for this room:");
                    mainService.setCurrentUserState(user, UserState.WAIT_FOR_PHOTO);
                }
                case "WAIT_FOR_PHOTO" -> {
                    var photoURL = message.getText();
                    currentRoom.setPhoto(photoURL);
                    String response =
                            "Current room info :\n" +
                            "       Room number: " + currentRoom.getNumber().toString() + "\n" +
                            "       Number of rooms inside: " + currentRoom.getNumberOfRooms().toString() + "\n" +
                            "       Description: " + currentRoom.getDescription() + "\n" +
                            "       Photo URL: " + currentRoom.getPhoto();
                    sendTextMessage(chatId, response);
                    mainService.saveNewHotelRoom(currentRoom);
                    mainService.setCurrentUserState(user, UserState.BASIC_STATE);
                }
                default -> sendTextMessage(message.getChatId().toString(), "Cannot define your message! Try again pls :)");
            }
        }
        else {
            sendTextMessage(chatId, "Your access level is too low to use this command!");
        }
    }

    public void sendTextMessage(String chatId, String text) {
        var response = new SendMessage();
        response.setChatId(chatId);
        response.setText(text);

        try {
            this.execute(response);
        } catch (TelegramApiException e) {
            log.error(e);
        }
    }

    public void sendPhotoMessage(String chatId, String text, String photoUrl) {
        var res = new SendMessage();
        var response = new SendPhoto();

        var photo = new InputFile(photoUrl);
        response.setParseMode(ParseMode.HTML);
        response.setChatId(chatId);
        response.setPhoto(photo);
        response.setCaption(text);

        try {
            this.execute(response);
        } catch (TelegramApiException e) {
            log.error(e);
        }
    }
}