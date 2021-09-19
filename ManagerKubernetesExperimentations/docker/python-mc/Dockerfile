FROM python:3.7-slim
WORKDIR /app
RUN apt-get update \
&& apt-get install -y gcc wget curl nano jq \
&& apt-get clean
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN wget https://dl.minio.io/client/mc/release/linux-amd64/mc -P /usr/bin/
RUN chmod +x /usr/bin/mc
RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl
RUN chmod +x ./kubectl
RUN mv ./kubectl /usr/local/bin/kubectl
RUN curl -LO https://get.helm.sh/helm-v3.4.0-linux-amd64.tar.gz 
RUN tar -zxvf helm-v3.4.0-linux-amd64.tar.gz
RUN mv ./linux-amd64/helm /usr/local/bin/helm
ENTRYPOINT ["python"]