# To build do e.g. docker build -t frontier/phusion .
# To run do e.g. docker run -it frontier/phusion 
# Or if you want a specific command:  docker run -it frontier/phusion <cmd> 
# To remove containers do docker rm <containername>
# To remove images do docker image prune <imagetag>

# To build a specific stage target:
# docker build --target=pi -t frontier/jessie . 
FROM raspbian/jessie as pi

ARG UID=1000
ARG GID=1000

RUN apt-get update && apt-get install -y \
  build-essential \
  curl \
  wget \
  make gcc g++ bc python3 maven openjdk-8-jdk libv4l-dev ant vim\
  sudo \
  ssh \
  git

RUN useradd --create-home -u ${UID} -s /bin/bash frontier && \
    adduser frontier sudo && \
    echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers 

WORKDIR /frontier

RUN chown -R frontier:frontier /frontier
RUN chmod ugo+rw /tmp

USER frontier 
ENV USER=frontier

RUN git clone https://github.com/dgc-rhul/frontier.git . 
#RUN apt-cache search openjdk-8
RUN /bin/bash -c "./frontier-bld.sh pi 2>&1 | tee build.log"

CMD ["/bin/bash"] 
