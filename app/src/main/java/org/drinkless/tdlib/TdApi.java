package org.drinkless.tdlib;

public final class TdApi {

    public static abstract class Object {
        public Object() {}
    }

    public static abstract class Function extends Object {
        public Function() {}
    }

    // --- Authorization States ---
    public static abstract class AuthorizationState extends Object {}

    public static class AuthorizationStateWaitTdlibParameters extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateWaitTdlibParameters"; }
    }

    public static class AuthorizationStateWaitPhoneNumber extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateWaitPhoneNumber"; }
    }

    public static class AuthorizationStateWaitCode extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateWaitCode"; }
    }

    public static class AuthorizationStateWaitPassword extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateWaitPassword"; }
    }

    public static class AuthorizationStateReady extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateReady"; }
    }

    public static class AuthorizationStateClosing extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateClosing"; }
    }

    public static class AuthorizationStateClosed extends AuthorizationState {
        @Override
        public String toString() { return "AuthorizationStateClosed"; }
    }

    // --- Updates ---
    public static class UpdateAuthorizationState extends Object {
        public AuthorizationState authorizationState;

        public UpdateAuthorizationState() {}
        public UpdateAuthorizationState(AuthorizationState authorizationState) {
            this.authorizationState = authorizationState;
        }
    }

    public static class UpdateFile extends Object {
        public File file;

        public UpdateFile() {}
        public UpdateFile(File file) {
            this.file = file;
        }
    }

    // --- Core Model Objects ---
    public static class Chat extends Object {
        public long id;
        public String title;
        public String type;

        public Chat() {}
        public Chat(long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public static class Message extends Object {
        public long id;
        public long chatId;
        public MessageContent content;

        public Message() {}
        public Message(long id, long chatId, MessageContent content) {
            this.id = id;
            this.chatId = chatId;
            this.content = content;
        }
    }

    public static class Messages extends Object {
        public Message[] messages;

        public Messages() {}
        public Messages(Message[] messages) {
            this.messages = messages;
        }
    }

    public static abstract class MessageContent extends Object {}

    public static class MessageVideo extends MessageContent {
        public Video video;
        public FormattedText caption;

        public MessageVideo() {}
        public MessageVideo(Video video, FormattedText caption) {
            this.video = video;
            this.caption = caption;
        }
    }

    public static class Video extends Object {
        public String fileName;
        public int duration;
        public int width;
        public int height;
        public File video;

        public Video() {}
        public Video(String fileName, int duration, int width, int height, File video) {
            this.fileName = fileName;
            this.duration = duration;
            this.width = width;
            this.height = height;
            this.video = video;
        }
    }

    public static class File extends Object {
        public int id;
        public long size;
        public long expectedSize;
        public LocalFile local;

        public File() {}
        public File(int id, long size, long expectedSize, LocalFile local) {
            this.id = id;
            this.size = size;
            this.expectedSize = expectedSize;
            this.local = local;
        }
    }

    public static class LocalFile extends Object {
        public String path;
        public boolean isDownloadingActive;
        public boolean isDownloadingCompleted;
        public long downloadedSize;

        public LocalFile() {}
        public LocalFile(String path, boolean isDownloadingActive, boolean isDownloadingCompleted, long downloadedSize) {
            this.path = path;
            this.isDownloadingActive = isDownloadingActive;
            this.isDownloadingCompleted = isDownloadingCompleted;
            this.downloadedSize = downloadedSize;
        }
    }

    public static class FormattedText extends Object {
        public String text;

        public FormattedText() {}
        public FormattedText(String text) {
            this.text = text;
        }
    }

    public static class Error extends Object {
        public int code;
        public String message;

        public Error() {}
        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static class Ok extends Object {}

    // --- TDLib API Functions (Requests) ---
    public static class SetTdlibParameters extends Function {
        public int apiId;
        public String apiHash;
        public String systemLanguageCode;
        public String deviceModel;
        public String systemVersion;
        public String applicationVersion;
        public boolean useMessageDatabase;
        public boolean useChatInfoDatabase;
        public boolean useSavedAnimationsDatabase;
        public boolean useFileDatabase;
        public String databaseDirectory;
        public boolean useSecretChats;

        public SetTdlibParameters() {}
    }

    public static class SetAuthenticationPhoneNumber extends Function {
        public String phoneNumber;

        public SetAuthenticationPhoneNumber() {}
        public SetAuthenticationPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    public static class CheckAuthenticationCode extends Function {
        public String code;

        public CheckAuthenticationCode() {}
        public CheckAuthenticationCode(String code) {
            this.code = code;
        }
    }

    public static class CheckAuthenticationPassword extends Function {
        public String password;

        public CheckAuthenticationPassword() {}
        public CheckAuthenticationPassword(String password) {
            this.password = password;
        }
    }

    public static class SearchPublicChat extends Function {
        public String username;

        public SearchPublicChat() {}
        public SearchPublicChat(String username) {
            this.username = username;
        }
    }

    public static class GetChatHistory extends Function {
        public long chatId;
        public long fromMessageId;
        public int offset;
        public int limit;
        public boolean onlyLocal;

        public GetChatHistory() {}
        public GetChatHistory(long chatId, long fromMessageId, int offset, int limit, boolean onlyLocal) {
            this.chatId = chatId;
            this.fromMessageId = fromMessageId;
            this.offset = offset;
            this.limit = limit;
            this.onlyLocal = onlyLocal;
        }
    }

    public static class DownloadFile extends Function {
        public int fileId;
        public int priority;
        public long offset;
        public long limit;
        public boolean synchronous;

        public DownloadFile() {}
        public DownloadFile(int fileId, int priority, long offset, long limit, boolean synchronous) {
            this.fileId = fileId;
            this.priority = priority;
            this.offset = offset;
            this.limit = limit;
            this.synchronous = synchronous;
        }
    }
}
