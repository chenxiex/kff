FROM mcr.microsoft.com/devcontainers/java:17-trixie

USER root

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}

ARG CMDLINE_TOOLS_VERSION=15859902
ARG CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

RUN set -eu; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools/latest"; \
    curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/android-commandline-tools.zip; \
    echo "${CMDLINE_TOOLS_SHA256}  /tmp/android-commandline-tools.zip" | sha256sum -c -; \
    unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools; \
    mv /tmp/android-commandline-tools/cmdline-tools/* "${ANDROID_HOME}/cmdline-tools/latest/"; \
    rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager "platforms;android-34" "build-tools;36.0.0" "platform-tools"; \
    chown -R vscode:vscode "${ANDROID_HOME}"
