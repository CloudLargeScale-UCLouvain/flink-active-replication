FROM python:alpine3.6
RUN apk --no-cache add curl jq ca-certificates openssl
RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl
RUN chmod +x ./kubectl
RUN mv ./kubectl /usr/local/bin/kubectl
RUN kubectl version --client
RUN wget https://dl.min.io/client/mc/release/linux-amd64/mc
RUN chmod +x mc
RUN mv ./mc /usr/local/bin/mc
RUN mc --help    
RUN pip install requests
