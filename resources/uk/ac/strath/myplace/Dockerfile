FROM ubuntu:20.10

ENV DEBIAN_FRONTEND="noninteractive"

# Install some basics.
RUN apt-get update && apt-get install -y unzip \
    curl git mysql-client mysql-server \
    postgresql postgresql-client \
    locales \
    # For add-apt-repository
    software-properties-common

RUN add-apt-repository ppa:ondrej/php

RUN for ver in 7.2 7.3 7.4 8.0; do apt-get install -y php$ver php$ver-curl php$ver-gd php$ver-intl \
    php$ver-mbstring php$ver-mysql php$ver-xml php$ver-zip; done \
    && update-alternatives --set php /usr/bin/php7.4

# Create a real jenkins user. Git will not work correctly if the current user is just an id without a user entry.
ARG JENKINS_USERID
RUN useradd -m -r -u ${JENKINS_USERID} jenkins

# Create a home directory for mysql so service can be started.
RUN mkdir -p /var/lib/mysql \
    && usermod -d /var/lib/mysql/ mysql \
    && service mysql start \
    && mysql -e "CREATE USER jenkins@localhost IDENTIFIED WITH mysql_native_password BY 'jenkins'" \
    && mysql -e "GRANT ALL PRIVILEGES ON *.* TO 'jenkins'@'localhost'"

# Allow jenkins user to start MySQL
RUN apt-get update && apt-get install sudo
RUN echo "jenkins ALL= NOPASSWD: /usr/sbin/service mysql start" > /etc/sudoers.d/jenkins
RUN echo "jenkins ALL= NOPASSWD: /usr/sbin/service postgresql start" >> /etc/sudoers.d/jenkins
RUN echo "jenkins ALL= NOPASSWD: /usr/bin/update-alternatives --set php /usr/bin/php*" >> /etc/sudoers.d/jenkins

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
ENV NODE_VERSION 14.18.0

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

# Install grunt CLI.
RUN npm install -g grunt-cli

USER postgres
# Create jenkins user. There's probably a better way to do this.
RUN service postgresql start \
    && createuser -s -i -d -r -l -w jenkins \
    && psql -c "ALTER ROLE jenkins WITH PASSWORD 'jenkins';"

USER jenkins
