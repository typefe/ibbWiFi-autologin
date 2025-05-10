FROM openjdk:17-jdk-slim

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_VERSION=8.4
ENV GRADLE_HOME=/opt/gradle
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${GRADLE_HOME}/bin

RUN apt-get update && apt-get install -y wget unzip git curl

# Install Gradle
RUN mkdir -p ${GRADLE_HOME} && \
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O gradle.zip && \
    unzip -d /opt gradle.zip && \
    mv /opt/gradle-${GRADLE_VERSION}/* ${GRADLE_HOME} && \
    rm -rf /opt/gradle-${GRADLE_VERSION} && \
    rm gradle.zip

# Install Android Command-Line Tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip -O cmdline-tools.zip && \
    unzip cmdline-tools.zip && \
    rm cmdline-tools.zip && \
    mv cmdline-tools latest

# Accept licenses and install tools
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

WORKDIR /project