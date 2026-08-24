import asyncio
import os
import sys
from telethon import TelegramClient
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
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
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
    if COMMIT_URL is None:
        print("[-] Invalid COMMIT_URL")
        exit(1)
    if COMMIT_MESSAGE is None:
        print("[-] Invalid COMMIT_MESSAGE")
        exit(1)
    if RUN_URL is None:
        print("[-] Invalid RUN_URL")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if VERSION is None:
        print("[-] Invalid VERSION")
        exit(1)
    # MESSAGE_THREAD_ID is optional. An empty value means upload to the chat root
    # instead of a forum topic, which is valid for channels and non-forum chats.
    if not MESSAGE_THREAD_ID or not MESSAGE_THREAD_ID.strip():
        MESSAGE_THREAD_ID = None
    else:
        try:
            MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID.strip())
        except ValueError:
            print("[-] Invalid MESSAGE_THREAD_ID: expected an integer when set")
            exit(1)


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
        caption = [""] * len(files)
        caption[-1] = get_caption()
        print("[+] Caption: ")
        print("---")
        print(caption)
        print("---")
        print("[+] Sending")
        send_kwargs = {
            "entity": CHAT_ID,
            "file": files,
            "caption": caption,
            "parse_mode": "markdown",
        }
        if MESSAGE_THREAD_ID is not None:
            send_kwargs["reply_to"] = MESSAGE_THREAD_ID
        else:
            print("[+] No Telegram thread configured; uploading to chat root")
        await bot.send_file(**send_kwargs)
        print("[+] Done!")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
