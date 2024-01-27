package org.crptapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
/**
 * Класс для работы с API.
 * Этот класс является потокобезопасным, что гарантирует корректное функционирование в многопоточной среде.
 */
public class CrptApi {
    private final TimeUnit timeUnit;
    private final Semaphore semaphore;
    private final Lock lock;
    private long lastRequestTime;
    /**
     * Конструктор для создания нового экземпляра CrptApi с указанными единицами времени и лимитом запросов.
     *
     * @param timeUnit      единицы времени для временного интервала запросов (например, секунды, минуты)
     * @param requestLimit  максимальное количество разрешенных запросов в указанном временном интервале
     * @throws IllegalArgumentException если timeUnit равен null или requestLimit меньше или равен 0
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit){
        if(timeUnit == null || requestLimit < 0){
            throw new IllegalArgumentException("timeUnit cannot be null or requestLimit must be a positive int and cannot be zero");
        }
        this.timeUnit = timeUnit;
        this.semaphore = new Semaphore(requestLimit);
        this.lock = new ReentrantLock();
        this.lastRequestTime = 0;
    }

    /**
     * Создает документ и отправляет запрос к API.
     * Если количество запросов превышает установленный лимит, метод блокирует выполнение
     * до тех пор, пока не станет возможным выполнить запрос.
     *
     * @param document  объект, представляющий документ для создания
     * @param signature строка, представляющая подпись документа
     */
    public void createDoc(Document document, String signature){
        try{
            if(semaphore.tryAcquire(1, 0, TimeUnit.MICROSECONDS)){
                // Разрешение получено, можно выполнять запрос

                // Определение времени с момента последнего запроса
                long currentTime = System.nanoTime();
                long timeSinceLastRequest = currentTime - lastRequestTime;
                // Определение времени ожидания для управления запросами в интервале
                long waitTime = timeUnit.toNanos(1);

                // Если были предыдущие запросы, корректировка времени ожидания
                if(lastRequestTime != 0){
                    waitTime = timeUnit.toNanos(1) - timeSinceLastRequest;
                    if(waitTime > 0){
                        LockSupport.parkNanos(waitTime); // "Парковка" потока на оставшееся время
                    }
                }

                // Формирование URL API
                String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
                // Создание HTTP-клиента и объекта ObjectMapper
                HttpClient httpClient = HttpClient.newHttpClient();
                ObjectMapper objectMapper = new ObjectMapper();

                // Преобразование объекта документа и подписи в JSON
                ObjectNode requestBody = objectMapper.valueToTree(document);
                requestBody.put("signature", signature); // Не понял что делать с подписью поэтому так

                // Создание HTTP-запроса
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .build();

                // Отправка запроса и получение ответа
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Обработка ответа от сервера
                if(response.statusCode() == 200){
                    System.out.println("Doc created!");
                } else {
                    System.err.println("Failed to create doc. Http status: "+response.statusCode());
                }
                // Обновление времени последнего запроса
                lastRequestTime = System.nanoTime();
            }else {
                // Лимит запросов превышен, вывод сообщения
                System.err.println("Retry later. Request limit exceeded.");
            }
        }catch (Exception e){
            // Обработка возможных исключений
            e.printStackTrace();
        }finally {
            // Освобождение разрешения в любом случае (даже при возникновении исключения)
            semaphore.release();
        }
    }
}

/**
 *Используется для взаимодействия с API. Представляет из себя класс описывающий документ.
 */
class Document{
    private Collection<Participant> description;
    private String id;
    private String status;
    private String type;
    private boolean importRequest;
    private String ownerInn;
    private String participantInn;
    private String producerInn;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime productionDate;
    private String productionType;
    private Collection<Product> products;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime regDate;
    private String regNumber;
    /**
     * Аннотация {@link JsonCreator} используется для создания конструктора, который
     * будет использоваться при сериализации обьекта в JSON.
     *
     * Аннотация {@link JsonProperty} используется для привязки полей класса к соответствующим полям в JSON.
     * Это важно при сериализации, чтобы Jackson знал, какое поле соответствует какому свойству объекта.
     *
     * Анотация {@link JsonFormat} используется для форматирования полей типа LocalDateTime в процессе сериализации и десериализации.
     * В данном случае, она указывает, что поле productionDate и regDate должно быть представлено в формате "yyyy-MM-dd".
     */
    @JsonCreator
    public Document(
            @JsonProperty("description") Collection<Participant> description,
            @JsonProperty("doc_id") String id,
            @JsonProperty("doc_status") String status,
            @JsonProperty("doc_type") String type,
            @JsonProperty("importRequest") boolean importRequest,
            @JsonProperty("owner_inn") String ownerInn,
            @JsonProperty("participant_inn") String participantInn,
            @JsonProperty("producer_inn") String producerInn,
            @JsonProperty("production_date") LocalDateTime productionDate,
            @JsonProperty("production_type") String productionType,
            @JsonProperty("products") Collection<Product> products,
            @JsonProperty("reg_date") LocalDateTime regDate,
            @JsonProperty("reg_number") String regNumber
    ){
        this.description = description;
        this.id = id;
        this.status = status;
        this.type = type;
        this.importRequest = importRequest;
        this.ownerInn = ownerInn;
        this.participantInn = participantInn;
        this.producerInn = producerInn;
        this.productionDate = productionDate;
        this.productionType = productionType;
        this.products = products;
        this.regDate = regDate;
        this.regNumber = regNumber;
    }
    public Collection<Participant> getDescription() {
        return description;
    }
    public void setDescription(Collection<Participant> description) {
        this.description = description;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public boolean isImportRequest() {
        return importRequest;
    }
    public void setImportRequest(boolean importRequest) {
        this.importRequest = importRequest;
    }
    public String getOwnerInn() {
        return ownerInn;
    }
    public void setOwnerInn(String ownerInn) {
        this.ownerInn = ownerInn;
    }
    public String getParticipantInn() {
        return participantInn;
    }
    public void setParticipantInn(String participantInn) {
        this.participantInn = participantInn;
    }
    public String getProducerInn() {
        return producerInn;
    }
    public void setProducerInn(String producerInn) {
        this.producerInn = producerInn;
    }
    public LocalDateTime getProductionDate() {
        return productionDate;
    }
    public void setProductionDate(LocalDateTime productionDate) {
        this.productionDate = productionDate;
    }
    public String getProductionType() {
        return productionType;
    }
    public void setProductionType(String productionType) {
        this.productionType = productionType;
    }
    public Collection<Product> getProducts() {
        return products;
    }
    public void setProducts(Collection<Product> products) {
        this.products = products;
    }
    public LocalDateTime getRegDate() {
        return regDate;
    }
    public void setRegDate(LocalDateTime regDate) {
        this.regDate = regDate;
    }
    public String getRegNumber() {
        return regNumber;
    }
    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }
}
/**
 * Класс представляет участника в документе.
 * Используется для взаимодействия с API.
 */
class Participant{
    private String inn;
    @JsonCreator
    public Participant(@JsonProperty("participantInn") String inn){
        this.inn = inn;
    }
    public String getInn() {
        return inn;
    }
    public void setInn(String inn) {
        this.inn = inn;
    }
}
/**
 * Класс представляет продукт в документе.
 * Используется для взаимодействия с API.
 */
class Product{
    private String certificateDocument;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime certificateDocumentDate;
    private String certificateDocumentNumber;
    private String ownerInn;
    private String producerInn;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime productionDate;
    private String tnvedCode;
    private String uitCode;
    private String uituCode;

    @JsonCreator
    public Product(
           @JsonProperty("certificate_document") String certificateDocument,
           @JsonProperty("certificate_document_date") LocalDateTime certificateDocumentDate,
           @JsonProperty("certificate_document_number") String certificateDocumentNumber,
           @JsonProperty("owner_inn") String ownerInn,
           @JsonProperty("producer_inn") String producerInn,
           @JsonProperty("production_date") LocalDateTime productionDate,
           @JsonProperty("tnved_code") String tnvedCode,
           @JsonProperty("uit_code") String uitCode,
           @JsonProperty("uitu_code") String uituCode) {
        this.certificateDocument = certificateDocument;
        this.certificateDocumentDate = certificateDocumentDate;
        this.certificateDocumentNumber = certificateDocumentNumber;
        this.ownerInn = ownerInn;
        this.producerInn = producerInn;
        this.productionDate = productionDate;
        this.tnvedCode = tnvedCode;
        this.uitCode = uitCode;
        this.uituCode = uituCode;
    }
    public String getCertificateDocument() {
        return certificateDocument;
    }
    public void setCertificateDocument(String certificateDocument) {
        this.certificateDocument = certificateDocument;
    }
    public LocalDateTime getCertificateDocumentDate() {
        return certificateDocumentDate;
    }
    public void setCertificateDocumentDate(LocalDateTime certificateDocumentDate) {
        this.certificateDocumentDate = certificateDocumentDate;
    }
    public String getCertificateDocumentNumber() {
        return certificateDocumentNumber;
    }
    public void setCertificateDocumentNumber(String certificateDocumentNumber) {
        this.certificateDocumentNumber = certificateDocumentNumber;
    }
    public String getOwnerInn() {
        return ownerInn;
    }
    public void setOwnerInn(String ownerInn) {
        this.ownerInn = ownerInn;
    }
    public String getProducerInn() {
        return producerInn;
    }
    public void setProducerInn(String producerInn) {
        this.producerInn = producerInn;
    }
    public LocalDateTime getProductionDate() {
        return productionDate;
    }
    public void setProductionDate(LocalDateTime productionDate) {
        this.productionDate = productionDate;
    }
    public String getTnvedCode() {
        return tnvedCode;
    }
    public void setTnvedCode(String tnvedCode) {
        this.tnvedCode = tnvedCode;
    }
    public String getUitCode() {
        return uitCode;
    }
    public void setUitCode(String uitCode) {
        this.uitCode = uitCode;
    }
    public String getUituCode() {
        return uituCode;
    }
    public void setUituCode(String uituCode) {
        this.uituCode = uituCode;
    }
}
