# Use Ubuntu 24.04 as the base image
FROM ubuntu:24.04

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK 25 and other dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-25-jdk \
    openjdk-25-jre \
    sudo \
    git \
    maven \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user with sudo access
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=1000

# Create the user and group if they don't exist
RUN if ! getent group $USER_GID >/dev/null; then \
        groupadd --gid $USER_GID $USERNAME; \
    fi && \
    if ! getent passwd $USER_UID >/dev/null; then \
        useradd --uid $USER_UID --gid $USER_GID -m $USERNAME; \
    fi && \
    echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME && \
    chmod 0440 /etc/sudoers.d/$USERNAME

# Set the working directory
WORKDIR /workspace

# Switch to the non-root user
USER $USER_UID:$USER_GID

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Default command
CMD ["/bin/bash"]