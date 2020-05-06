# To build do e.g. docker build -t frontier/phusion .
# To run do e.g. docker run -it frontier/phusion 
# Or if you want a specific command:  docker run -it frontier/phusion <cmd> 
# To remove containers do docker rm <containername>
# To remove images do docker image prune <imagetag>

# To build a specific stage target:
# docker build --target=pi -t frontier/jessie . 

# was ubuntu:16.04
FROM phusion/baseimage as ubuntu 
#FROM ubuntu:18.04 as builder

ARG UID=1000
ARG GID=1000

RUN apt-get update && apt-get install -y \
  build-essential \
  curl \
  wget \
  make gcc g++ bc python3 maven openjdk-8-jdk-headless libv4l-dev ant vim\
  sudo \
  ssh \
  git

RUN useradd --create-home -u ${UID} -s /bin/bash frontier && \
    adduser frontier sudo && \
    echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers 

WORKDIR /frontier

RUN chown -R frontier:frontier /frontier

USER frontier 
ENV USER=frontier

RUN git clone https://github.com/dgc-rhul/frontier.git . 
RUN /bin/bash -c "./frontier-bld.sh core 2>&1 | tee build.log"

CMD ["/bin/bash"] 
