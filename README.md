# WIFI Direct transfer module

Небольшой плафтормо-независимый модуль, содержащий логику общения для WiFi direct клиентов на [Android](https://github.com/RAZRULETEL/WIFI_direct_Android) и [Windows](https://github.com/RAZRULETEL/WIFI_direct_Windows)

Модуль полностью независим от реализаций существующих клиентов, что позволяет его использовать для написания новых под другие платформы.

Для подключения:

   ```gradle
   repositories { 
        jcenter()
        maven { url "https://jitpack.io" }
   }
   dependencies {
         implementation 'com.github.RAZRULETEL:WIFI_direct_transfer:master-SNAPSHOT'
   }
   ```  
