ARG VARIANT="21"
FROM mcr.microsoft.com/devcontainers/java:1-${VARIANT}-bookworm

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/37.0.0

RUN apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get install -y --no-install-recommends curl unzip lib32stdc++6 lib32z1 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl --fail --location --silent --show-error \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        --output /tmp/android-commandline-tools.zip \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" \
    && chown -R vscode:vscode ${ANDROID_HOME}
