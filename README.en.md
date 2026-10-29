# AppReseal

[中文](README.md) | English | [日本語](README.ja.md)

A tool for you or a small team. Pick an App Store app, or upload your own encrypted IPA, then download the result when the job finishes.

The user does not need a jailbroken device. Download, decrypt, and verification run on the server. There is no wallet, price, or top-up.

Screenshots and the full guide are in [README.md](README.md) (Chinese) and [README.ja.md](README.ja.md) (Japanese). The interface itself is still Chinese.

**Disclaimer: for learning and research only.** Do not use it to distribute pirated apps, bypass payments, or break the law.

## Install

Docker Desktop and network access are required. The first build compiles the decrypt tools and takes a few minutes.

```bash
git clone <your-repo-url> appreseal
cd appreseal
docker compose up -d --build
```

Open http://127.0.0.1:8088.

| Role | Email | Password |
| --- | --- | --- |
| User | `demo@appreseal.com` | `demo1234` |
| Admin | `admin@appreseal.com` | `admin1234` |

Change `APPRESEAL_JWT_SECRET` and the database passwords before exposing the service. They are local demo values.

## Use

1. Sign in as admin and open **Apple ID pool**.
2. Log in an Apple ID. The password is used once and is not stored.
3. Obtain that account's `credential.bin` by following upstream [fairplay-decrypt](https://github.com/EthanArbuckle/fairplay-decrypt), then import it. The bundled [`backend/docker/fairplay/fairplay-decrypt`](backend/docker/fairplay/fairplay-decrypt) only decrypts: `decrypt.py --credential` expects a saved file of exactly 16 bytes and does not generate it. Apple ID login does not either. Mark the storefront and leave the account enabled.
4. As a user, create a job from the App Store or by uploading an IPA.
5. Download the result from the task page. Interrupted uploads and downloads can resume.
6. Cancel a queued or running job to stop it. Nothing is charged, so there is no refund.

`credential.bin`, session files, and IPA packages stay in the Docker volume. Do not commit them.

## Disclaimer

This project is for learning and research only.

Only analyse packages you have the right to analyse. Do not use it to distribute pirated apps, bypass payments, or break the law. You are responsible for complying with the law where you are, and for the consequences of using this software. It is provided as is. The authors and contributors accept no liability.

This notice is a usage reminder, not a licence term. The code remains under the MIT licence below.

## License

[MIT](LICENSE). You can use, modify, and redistribute this software if you keep the copyright notice. It is provided as is, without warranty.
