SafeDB 라이브러리 jar 배치 안내 (기술 설계서 TD-04)

SafeDB jar는 라이선스가 있는 상용 라이브러리라 Git 저장소에 포함하지 않는다.
빌드 전에 사내에서 pgweb-boot 프로젝트의 libs/safedb-lib/ 에 있는 아래 9개 파일을
이 폴더(backend/libs/safedb-lib/)로 그대로 복사한다. 파일이 9개가 아니면 빌드가 중단된다.

  INICrypto_v4.1.19.jar
  INISAFECore_v2.1.31.jar
  INISAFENet_v7.2.21.jar
  INISAFEPKI_v1.1.16.jar
  SafeDBClient_v2.3.20.jar
  SafeDBCrypto_v2.3.18.jar
  SafeDBFilter_v2.3.6.jar
  xercesImpl.jar
  xml-apis.jar

배포 zip(gradlew distZip)은 이 폴더의 jar를 lib/ 에 넣으므로, 담당자 PC에는 zip만 전달하면 된다.
