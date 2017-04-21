# Docker images for [Tock](https://github.com/voyages-sncf-technologies/tock)

## Build images

You will need [Maven](https://maven.apache.org/) and [Git](https://git-scm.com/).

```sh 
    git clone https://github.com/voyages-sncf-technologies/tock-docker.git
    cd tock-docker
    mvn package docker:build
```

## Start NLP administration interface

```sh 
    docker-compose up
``` 

## Start Open Data Bot example

```sh 
    docker-compose -f docker-compose-bot-open-data.yml up
``` 