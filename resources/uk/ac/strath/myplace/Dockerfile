FROM ubuntu:20.10

ENV DEBIAN_FRONTEND="noninteractive"

# Install some basics.
# PHP appears to be needed for phpenv to install its versions.
RUN apt update && apt-get install -y php unzip \
    curl git docker.io mysql-client mysql-server \
    postgresql postgresql-client \
    locales

# For phpenv
RUN echo deb-src http://archive.ubuntu.com/ubuntu/ groovy main restricted >> /etc/apt/sources.list
RUN apt-get update && apt-get install -y libreadline-dev && apt-get build-dep -y php7.4

# Create a real jenkins user. Jenkins launches containers with the user and group id of the jenkins user on the host.
RUN mkdir /home/jenkins && groupadd -g 119 jenkins && groupmod -g 120 docker && useradd -r -u 113 -g jenkins jenkins \
    && chown jenkins:jenkins /home/jenkins

# Create a home directory for mysql so service can be started.
RUN mkdir -p /var/lib/mysql \
    && usermod -d /var/lib/mysql/ mysql \
    && service mysql start \
    && mysql -e "CREATE USER jenkins@localhost IDENTIFIED BY 'jenkins'" \
    && mysql -e "GRANT ALL PRIVILEGES ON *.* TO 'jenkins'@'localhost'"

# Allow jenkins user to start MySQL
RUN apt-get update && apt-get install sudo
RUN echo "jenkins ALL= NOPASSWD: /usr/sbin/service mysql start" > /etc/sudoers.d/jenkins

# Remove apt cache
RUN rm -rf /var/lib/apt/lists/*

# Install composer.
ENV COMPOSER_HOME /composer
RUN mkdir $COMPOSER_HOME && chmod 777 $COMPOSER_HOME
RUN curl https://getcomposer.org/download/2.0.11/composer.phar -o composer.phar \
    && chmod +x composer.phar \
    && mv composer.phar /usr/local/bin/composer

# Add locale required by unit tests.
RUN locale-gen en_AU.UTF-8

# Install nvm to install node (default ubuntu one is too old and, more importantly, the version of
# node is defined in the Moodle packages.json)
# The install script should actually try and install the version from Moodle's .nvmrc anyway
# but we need it to install the global grunt CLI (unless this is done by the install, who knows?)
# Also it's needed for the post-install step of the create project for moodle-plugin-ci
# From https://stackoverflow.com/questions/25899912/how-to-install-nvm-in-docker
ENV NVM_DIR /var/lib/nvm
ENV NODE_VERSION 14.15.0

RUN mkdir $NVM_DIR \
    && chown jenkins:jenkins $NVM_DIR

USER jenkins

# Install NVM
RUN curl https://raw.githubusercontent.com/nvm-sh/nvm/v0.37.2/install.sh | bash \
    && . $NVM_DIR/nvm.sh \
    && nvm install $NODE_VERSION \
    && nvm alias default $NODE_VERSION \
    && nvm use default

ENV NODE_PATH $NVM_DIR/versions/node/v$NODE_VERSION/lib/node_modules
ENV PATH      $NVM_DIR/versions/node/v$NODE_VERSION/bin:$PATH

# Install phpenv
RUN curl -L https://raw.githubusercontent.com/phpenv/phpenv-installer/master/bin/phpenv-installer | bash

ENV PHPENV_ROOT="/home/jenkins/.phpenv"
ENV PATH $PHPENV_ROOT/shims:$PHPENV_ROOT/bin:$PATH

# Keep these in separate layers so we can add new versions without having to wait for all the existing installs
# to rebuild.
# RUN phpenv install 7.2.34
# RUN phpenv install 7.3.27
RUN phpenv install 7.4.16
RUN phpenv install 8.0.3

# Default to 7.4
RUN phpenv global 7.4

# Install grunt CLI.
RUN npm install -g grunt-cli
