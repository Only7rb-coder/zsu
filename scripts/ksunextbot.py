import asyncio
import os
import sys
from telethon import TelegramClient, functions, types
from telethon.tl.functions.help import GetConfigRequest

# Environment Variables
API_ID = os.environ.get("API_ID")
API_HASH = os.environ.get("API_HASH")
BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
COMMIT_URL = os.environ.get("COMMIT_URL")
COMMIT_MESSAGE = os.environ.get("COMMIT_MESSAGE")
RUN_URL = os.environ.get("RUN_URL")
TITLE = os.environ.get("TITLE")
VERSION = os.environ.get("VERSION")
EXPECTED_TOPIC_NAME = "Root ZSU"
# The user-provided forum URL is https://t.me/c/2012558636/39336.
# Telegram supergroup IDs use the -100 prefix for this internal ID.
EXPECTED_CHAT_ID = -1002012558636
MSG_TEMPLATE = """
**{title}**
#ci_{version}
```
{commit_message}
```
[Commit]({commit_url})
[Workflow run]({run_url})
""".strip()


def get_caption():
    msg = MSG_TEMPLATE.format(
        title=TITLE,
        version=VERSION,
        commit_message=COMMIT_MESSAGE,
        commit_url=COMMIT_URL,
        run_url=RUN_URL,
    )
    if len(msg) > 1024:
        return COMMIT_URL
    return msg


def check_environ():
    global CHAT_ID, MESSAGE_THREAD_ID
    if not BOT_TOKEN or not BOT_TOKEN.strip():
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if not API_ID or not API_ID.strip():
        print("[-] Invalid API_ID")
        exit(1)
    if not API_HASH or not API_HASH.strip():
        print("[-] Invalid API_HASH")
        exit(1)
    if not CHAT_ID or not CHAT_ID.strip():
        print("[-] Invalid CHAT_ID")
        exit(1)
    else:
        try:
            CHAT_ID = int(CHAT_ID.strip())
        except ValueError:
            print("[-] Invalid CHAT_ID: expected an integer")
            exit(1)
    if CHAT_ID != EXPECTED_CHAT_ID:
        print(
            f"[-] Refusing to upload: CHAT_ID {CHAT_ID} is not the Root ZSU forum group "
            f"({EXPECTED_CHAT_ID})"
        )
        exit(1)
    if not COMMIT_URL:
        print("[-] Invalid COMMIT_URL")
        exit(1)
    if not COMMIT_MESSAGE:
        print("[-] Invalid COMMIT_MESSAGE")
        exit(1)
    if not RUN_URL:
        print("[-] Invalid RUN_URL")
        exit(1)
    if not TITLE:
        print("[-] Invalid TITLE")
        exit(1)
    if not VERSION:
        print("[-] Invalid VERSION")
        exit(1)
    # Manager releases must always be sent directly into the configured forum topic.
    # Never fall back to the chat root: a missing topic is a hard failure.
    if not MESSAGE_THREAD_ID or not MESSAGE_THREAD_ID.strip():
        print("[-] Missing MESSAGE_THREAD_ID: refusing to upload outside the Root ZSU topic")
        exit(1)
    try:
        MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID.strip())
    except ValueError:
        print("[-] Invalid MESSAGE_THREAD_ID: expected the Root ZSU forum topic ID")
        exit(1)
    if MESSAGE_THREAD_ID <= 0:
        print("[-] Invalid MESSAGE_THREAD_ID: expected a positive forum topic ID")
        exit(1)


async def verify_root_zsu_topic(bot):
    """Confirm the configured destination contract before sending.

    Telethon's installed layer does not expose the forum-topic listing request,
    and Telegram does not return topic titles through the Bot API. The user-
    supplied topic ID is therefore the authoritative destination identifier;
    the send response is checked below to prove the resulting message stayed
    in that topic.
    """
    print(
        f"[+] Using Telegram group {EXPECTED_CHAT_ID} and forum topic "
        f"'{EXPECTED_TOPIC_NAME}' (topic ID {MESSAGE_THREAD_ID})"
    )


def extract_sent_message(response):
    """Return the message object returned by SendMediaRequest, if present."""
    for update in getattr(response, "updates", []) or []:
        message = getattr(update, "message", None)
        if message is not None and getattr(message, "id", None) is not None:
            return message
    return None


async def refresh_sent_message(bot, message_id):
    """Refresh a sent message so Telegram supplies its persisted reply header."""
    for attempt in range(3):
        try:
            refreshed = await bot.get_messages(CHAT_ID, ids=message_id)
        except Exception:
            refreshed = None
        if refreshed is not None and getattr(refreshed, "id", None) == message_id:
            return refreshed
        if attempt < 2:
            await asyncio.sleep(1)
    return None


async def main():
    print("[+] Uploading to telegram")
    check_environ()
    files = sys.argv[1:]
    print("[+] Files:", files)
    if len(files) <= 0:
        print("[-] No files to upload")
        exit(1)
    print("[+] Logging in Telegram with bot")
    script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    session_dir = os.path.join(script_dir, "ksunextbot")
    async with await TelegramClient(session=session_dir, api_id=API_ID, api_hash=API_HASH).start(bot_token=BOT_TOKEN) as bot:
        await verify_root_zsu_topic(bot)
        caption = [""] * len(files)
        caption[-1] = get_caption()
        print("[+] Caption: ")
        print("---")
        print(caption)
        print("---")
        print("[+] Sending")
        # Telethon's high-level send_file(reply_to=...) creates an
        # InputReplyToMessage without top_msg_id. Telegram therefore does
        # not reliably place the upload inside a forum topic. Build the
        # media request explicitly and set both topic fields.
        print(f"[+] Uploading directly to Root ZSU forum topic {MESSAGE_THREAD_ID}")
        for file_path, file_caption in zip(files, caption):
            uploaded_file = await bot.upload_file(file_path)
            parsed_caption, entities = await bot._parse_message_text(
                file_caption,
                "markdown",
            )
            media = types.InputMediaUploadedDocument(
                file=uploaded_file,
                mime_type="application/vnd.android.package-archive",
                attributes=[
                    types.DocumentAttributeFilename(os.path.basename(file_path))
                ],
                force_file=True,
            )
            reply_to = types.InputReplyToMessage(
                reply_to_msg_id=MESSAGE_THREAD_ID,
                top_msg_id=MESSAGE_THREAD_ID,
            )
            response = await bot(
                functions.messages.SendMediaRequest(
                    peer=CHAT_ID,
                    media=media,
                    reply_to=reply_to,
                    message=parsed_caption,
                    entities=entities,
                )
            )
            sent_message = extract_sent_message(response)
            if sent_message is None:
                raise RuntimeError(
                    f"Telegram did not return the uploaded message for {os.path.basename(file_path)}"
                )
            # The raw SendMedia response may omit the persisted reply header.
            # Refresh the message before deciding whether Telegram threaded it.
            refreshed_message = await refresh_sent_message(bot, sent_message.id)
            if refreshed_message is not None:
                sent_message = refreshed_message
            sent_reply = getattr(sent_message, "reply_to", None)
            sent_top_id = getattr(sent_reply, "reply_to_top_id", None)
            if sent_top_id is None:
                sent_top_id = getattr(sent_reply, "top_msg_id", None)
            sent_reply_id = getattr(sent_reply, "reply_to_msg_id", None)
            if sent_top_id != MESSAGE_THREAD_ID or sent_reply_id != MESSAGE_THREAD_ID:
                raise RuntimeError(
                    f"Telegram returned a non-topic message for {os.path.basename(file_path)}: "
                    f"reply_to_msg_id={sent_reply_id}, top_msg_id={sent_top_id}"
                )
            print(
                f"[+] Sent {os.path.basename(file_path)} directly to "
                f"Root ZSU topic {MESSAGE_THREAD_ID} as message {sent_message.id}"
            )
        print("[+] Done! Root ZSU topic-only upload and verification completed")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
        raise
