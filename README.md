# kinesis-to-amp
Sample showing how to publish kinesis events to AWS managed prometheus

1. Compile and build lambda jar
   ```shell
   mvn clean package
   ```
   
3. Run terraform to set everything up
   ```shell
   terraform init
   terraform apply -auto-approve
   ``` 
   
