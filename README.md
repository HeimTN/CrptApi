# CrptApi

Java класс для работы с API Честного знака. Класс обеспечивает потокобезопасность и поддерживает ограничение на количество запросов к API в определенный временной интервал.

## Использование

```java
// Создание экземпляра CrptApi с ограничением на 10 запросов в минуту
CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);

// Создание документа для ввода в оборот товара
Document document = new Document(/* заполните поля документа */);
String signature = "some_signature";

// Вызов метода создания документа
crptApi.createDocument(document, signature);
```
## Методы

### CrptApi

#### `public CrptApi(TimeUnit timeUnit, int requestLimit)`

Конструктор класса CrptApi. Инициализирует объект с указанным временным интервалом и лимитом запросов.

- `timeUnit`: Промежуток времени (секунда, минута и т.д.).
- `requestLimit`: Максимальное количество запросов в указанном временном интервале.

#### `public void createDocument(Document document, String signature)`

Метод для создания документа для ввода в оборот товара. Вызывается по HTTPS методом POST на URL "https://ismp.crpt.ru/api/v3/lk/documents/create". В теле запроса передается JSON-документ.

- `document`: Объект Java, представляющий документ.
- `signature`: Строка, содержащая подпись документа.

## Формат JSON-документа

```json
{
  "description": {
    "participantInn": "string"
  },
  "doc_id": "string",
  "doc_status": "string",
  "doc_type": "LP_INTRODUCE_GOODS",
  "importRequest": true,
  "owner_inn": "string",
  "participant_inn": "string",
  "producer_inn": "string",
  "production_date": "2020-01-23",
  "production_type": "string",
  "products": [
    {
      "certificate_document": "string",
      "certificate_document_date": "2020-01-23",
      "certificate_document_number": "string",
      "owner_inn": "string",
      "producer_inn": "string",
      "production_date": "2020-01-23",
      "tnved_code": "string",
      "uit_code": "string",
      "uitu_code": "string"
    }
  ],
  "reg_date": "2020-01-23",
  "reg_number": "string"
}
```
## Зависимости
- `java.net.http`: Для выполнения HTTP-запросов.
- `com.fasterxml.jackson.databind`: Для сериализации/десериализации JSON.
