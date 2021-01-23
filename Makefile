.PHONY: localTest startContainer release

localTest:
	docker stop minio1 || echo already stopped
	docker run --rm -d -p 9000:9000 --name minio1 -e "MINIO_ACCESS_KEY=access_key"  -e "MINIO_SECRET_KEY=secret_key"  minio/minio server /data
	sbt clean test || docker stop minio1
	docker stop minio1


startContainer:
	docker stop minio1 || echo already stopped
	docker run --rm -d -p 9000:9000 --name minio1 -e "MINIO_ACCESS_KEY=access_key"  -e "MINIO_SECRET_KEY=secret_key"  minio/minio server /data

release:
	docker stop minio1 || echo already stopped
	docker run --rm -d -p 9000:9000 --name minio1 -e "MINIO_ACCESS_KEY=access_key"  -e "MINIO_SECRET_KEY=secret_key"  minio/minio server /data
	sbt clean "release with-defaults" || docker stop minio1
	docker stop minio1