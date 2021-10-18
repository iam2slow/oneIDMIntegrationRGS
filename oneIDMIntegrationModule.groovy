
/**
 * author:aseleznev
 * since 23/09/2021
 * Модуль для интеграции Service Desk с IDM
 */

//ПАРАМЕТРЫ
/*
BRFQN = 'businessRole$businessRole'
BASE_URL = 'https://idm-app-test.rgs.ru'
User = 'integration.naumen'
Password = 'integration.nuamen'
*/
import groovy.json.JsonOutput

/**
 * Залогировать сообщение
 */
void log(def level = null, def message) {
    def LOGGING_IS_ENABLED = true
    def prefix = 'integration1IDM | '
    def loggerLevel = level ? level.toLowerCase() : 'info'
    if (LOGGING_IS_ENABLED) {
        logger."${loggerLevel}" (prefix + message.toString())

    }
}




def Authorization1IDM(BASE_URL,User,Password)
{
    def url = "${BASE_URL}"+'/appserver/auth/apphost'
    def restClient = new groovyx.net.http.RESTClient();
    // таймаут на чтение
    restClient.getClient().getParams().setParameter('http.socket.timeout', 10000)
    // таймаут на установку соединения
    restClient.getClient().getParams().setParameter('http.connection.timeout', 10000)
    // формируем тело запроса
    def body=[authString : "Module=DialogUser;User=${User};Password=${Password}"]
    body = JsonOutput.toJson(body)
    // выполняем запрос
    def result = null;
    try
    {
        def response = restClient.post(['uri': url, 'requestContentType' :  groovyx.net.http.ContentType.JSON, 'body': body]);
        if(response.status == 200)
        {
            result = [cookie: "ss-opt=temp; ss-id=${response.data.sessionId}"]

        }
        else
        {
            log('error',"Authorization | Ответ не был получен. Статус: ${response.status}.");
        }
    }
    catch(e)
    {
        log('error',"Authorization | Ошибка: ${e.getMessage()}");
    }
    if(result)
    {

        log("Authorization | Токен получен");
    }
    restClient.shutdown();
    return result
}

def GetCatalogueFrom1IDM(BASE_URL,cookie)
{
    def url = "${BASE_URL}/appserver/api/entities/CCCV_BusinessRoles4Naumen?loadType=BulkReadOnly";
    def restClient = new groovyx.net.http.RESTClient();
    // таймаут на чтение
    restClient.getClient().getParams().setParameter('http.socket.timeout', 10000)
    // таймаут на установку соединения
    restClient.getClient().getParams().setParameter('http.connection.timeout', 10000)

    // выполняем запрос
    def result = null;
    try
    {
        def response = restClient.get(['uri': url, 'headers' : cookie, 'requestContentType' :  groovyx.net.http.ContentType.JSON]);
        if(response.status == 200)
        {
            result = response.data;
        }
        else
        {
            log('error',"GetCatalogFrom1IDM | Ответ не был получен. Статус: ${response.status}.");
        }
    }
    catch(e)
    {
        log('error',"GetCatalogFrom1IDM | Ошибка: ${e.getMessage()}");
    }
    if(result)
    {

        log("GetCatalogFrom1IDM | Запрос выполнен успешно");
    }
    restClient.shutdown();
    return result


}

void SearchNSDBRItem(CatalogueData1IDM,BRFQN)
{
    CatalogueData1IDM.each{
        def NSDBR = utils.findFirst(BRFQN, [idHolder1IDM: it.values.BRIdent])
        if(NSDBR)
        {log("SearchNSDCatalogueItems | Найдена бизнес-роль ${NSDBR.title} (${NSDBR.UUID}) с идентификатором ${it.values.BRIdent}")
            UpdateNSDBRItem(NSDBR, it.values,BRFQN)
        }
        else
        {log( "SearchNSDCatalogueItems | Не найдена бизнес-роль с идентификатором ${it.values.BRIdent}. Создаем новую бизнес-роль.")
            CreateNSDBRItem(it.values, BRFQN)
        }
    }
}



def CreateNSDBRItem(BRValues,BRFQN)
{
    def attrs = [idHolder1IDM : BRValues.BRIdent, title : BRValues.BRName, description : BRValues.BRDescription ]
    if(BRValues.BROwnersCardIdS) {
        def roleOwnersIds = BRValues.BROwnersCardIdS.split(',')
        def roleOwners = []
        roleOwnersIds.each {
            def roleOwner = utils.findFirst('employee$employee', [idHolder: it])
            if (roleOwner) {
                log("Найден владелец роли ${BRValues.BRName} с кодом карты ${it} - ${roleOwner.UUID}")
                roleOwners+=roleOwner

            }
            else {
                log("Не удалось найти владельца роли ${BRValues.BRName} с кодом карты ${it}")
            }
        }
        if(roleOwners) attrs.roleOwners = roleOwners
    }

    def newBR = null
    try
    {
        newBR = api.tx.call{utils.create (BRFQN,attrs)}

        log("CreatedNSDBRItem | Создана новая бизнес-роль: ${newBR.UUID}");
    }
    catch(e)
    {
        log('error',"CreateNSDBRItem | ERROR: ${e.toString()}");
    }
    return newBR
}

def UpdateNSDBRItem(NSDBR,BRValues,BRFQN)
{
    def attrs = [idHolder1IDM : BRValues.BRIdent, title : BRValues.BRName, description : BRValues.BRDescription ]
    if(BRValues.BROwnersCardIdS) {
        def roleOwnersIds = BRValues.BROwnersCardIdS.toString().split(',')
        log(roleOwnersIds)
        def roleOwners = []
        roleOwnersIds.each {
            def roleOwner = utils.findFirst('employee$employee', [idHolder: it])
            if (roleOwner) {
                log("Найден владелец роли ${BRValues.BRName} с кодом карты ${it} - ${roleOwner.UUID}")
                roleOwners+=roleOwner

            }
            else {
                log("Не удалось найти владельца роли ${BRValues.BRName} с кодом карты ${it}")
            }
        }
        if(roleOwners) attrs.roleOwners = roleOwners
    }

    def UpdatedBR = null
    try
    {
        UpdatedBR=api.tx.call{utils.edit(NSDBR,attrs)}

        log("UpdateNSDBRItem | Обновлена бизнес-роль: ${NSDBR.UUID}");
    }
    catch(e)
    {
        log('error',"UpdateNSDBRItem | ERROR: ${e.toString()}");
    }
    return  UpdatedBR
}

def CreateIDMInc(BASE_URL,cookie,Role_idHolder,PersonOrdered,SC_UUID)
{
    def url = "${BASE_URL}/appServer/api/script/test_CreatePWO";
    def restClient = new groovyx.net.http.RESTClient();
    // таймаут на чтение
    restClient.getClient().getParams().setParameter('http.socket.timeout', 10000)
    // таймаут на установку соединения
    restClient.getClient().getParams().setParameter('http.connection.timeout', 10000)
    // формируем тело запроса
    def body=["values" : ["UID_Org=${Role_idHolder};PersonOrdered=${PersonOrdered};OrderDetail1=${SC_UUID}"]]

    body = JsonOutput.toJson(body)
    // выполняем запрос
    def result = null;
    try
    {
        def response = restClient.put(['uri': url, 'headers' : cookie, 'requestContentType' :  groovyx.net.http.ContentType.JSON, 'body': body]);
        if(response.status == 200)
        {
            result = response.result

        }
        else
        {
            log('error',"CreateIDMInc | Ответ не был получен. Статус: ${response.status}.");
        }
    }
    catch(e)
    {
        log('error',"CreateIDMInc | Ошибка: ${e.getMessage()}");
    }
    if(result)
    {
        log("CreateIDMInc | ${result.result.toString()}");
    }
    restClient.shutdown();
    return result
}