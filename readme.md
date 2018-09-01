SparkingLambda
====== 

Het doel van deze workshop is het opzetten van een `AWS Lambda API` door middel van de micro webservice [Spark]. (niet Apache Spark!)   
Op deze manier kan je erg snel een webservice draaien voor je applicatie waarmee erg veel uit handen wordt genomen zoals oneindige schaling, het exposen van HTTP endpoints, authenticatie, interne monitoring, betaling naar gebruik etc.   
Daarnaast maken we gebruik van Kotlin en het veelbelovende Spek framework.


### Prerequisites
------
- [AWS](https://aws.amazon.com/free/) account
- Intellij Idea (community edition is voldoende)
- Kotlin
    * Installeren via Intellij Idea `Preferences -> plugins`
- Docker
    * Instructies op [Docker]
- Python en PIP
    * Indien je op Mac OSx zit en Homebrew hebt geinstallereed `brew install python`, anders 
    * Windows https://matthewhorne.me/how-to-install-python-and-pip-on-windows-10/
- SAM Local CLI
    * Installeren via Pip : `pip install aws-sam-cli`
    * Na het uitvoeren van `sam --version` zou de huidige versie geprint moeten worden
    * Issues met Python, pip en SAM installeren? Wellicht kan deze [issue](https://github.com/awslabs/aws-sam-cli/issues/509) je uit de brand helpen 

### Voordat we beginnen 
------

Om een vliegende start te maken met de workshop hebben we in de github repo een folder met de naam `part0` aangemaakt. Hierin staat het template waarin gradle geconfigureerd is voor Idea.    

Ook is de oplossing voor ieder hoofdstuk te vinden onder de namen part1, part2 en part3 respectievelijk.

Geen ervaring met gradle? Met de optie _use default gradle wrapper (recommended)_ wordt gradle bij je project gedownload door Idea en is de configuratie afdoende.

In deze readme wordt vaak de notatie van drie punten `...` gebruikt om aan te geven dat er iets moet worden toegevoegd aan een bestaande configuratie, bestand of code. Het snippet hieronder zou betekenen dat je in de configuratie van je buildScript, het repositories object **aanvult** met de regel `jcenter()`, en niet hetgeen wat onder repositories staat vervangt (of de hele file).
```gradle
buildscript {
    repositories {
        ...
        jcenter()
    }
}
```


## 1. Initiële setup
___

Het eerste wat we moeten doen om onze Lambda te programmeren in het toevoegen van de gradle depencendenies.  
Voeg onderstaande code toe aan de file genaamd **gradle.build**.

Eerst voegen we ShadowJar toe. Dit is een Gradle plugin om met een enkel commando een fat-JAR te builden.  
Later in de workshop maak je gebruik van ShadowJar door middel van het commando `./gradlew shadowJar` in de terminal in te voeren.

```gradle
buildscript {
    repositories {
        ...
        jcenter()
    }
    dependencies {
        ...
        classpath 'com.github.jengelman.gradle.plugins:shadow:2.0.4'
    }
}

apply plugin: 'com.github.johnrengelman.shadow'
apply plugin: 'java'
```

Onderstaande dependencies zijn de benodigdheden om een Spark server te runnen in een AWS Lambda.  
Log4J wordt toegevoegd om de logging naar AWS Cloudwatch te doen indien er in de Lambda errors optreden.
- [Spark](https://mvnrepository.com/artifact/com.sparkjava/spark-core)
- [AWS Serverless Container Spark](https://mvnrepository.com/artifact/com.amazonaws.serverless/aws-serverless-java-container-spark)
- [AWS Java SDK Lambda](https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk-lambda)
- [AWS Lambda Log4j](https://mvnrepository.com/artifact/com.amazonaws/aws-lambda-java-log4j/1.0.0) en [AWS Log4j simple](https://mvnrepository.com/artifact/org.slf4j/slf4j-simple/1.7.25)

```gradle
buildscript {
    ...
    ext.kotlin_version = '1.2.31'
    ext.aws_container_spark_version = '1.1'
    ext.aws_java_lambda_sdk_version = '1.11.342'
    ext.jackson_kotlin_version = '2.9.+'
    ext.spark_version = '2.7.2'
    ...
}
...
dependencies {
    ...
    compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version"
    compile "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
    compile "com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_kotlin_version"
    compile group: 'com.sparkjava', name: 'spark-core', version: "$spark_version"
    compile group: 'com.amazonaws.serverless', name: 'aws-serverless-java-container-spark', version: "$aws_container_spark_version"
    compile group: 'com.amazonaws', name: 'aws-java-sdk-lambda', version: "$aws_java_lambda_sdk_version"
    compile group: 'com.amazonaws', name: 'aws-lambda-java-log4j', version: '1.0.0'
    compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
    testCompile group: 'org.slf4j', name: 'slf4j-log4j12', version: '1.7.25'
    ...
}

compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
}
compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
}
```

Na het opslaan van `build.gradle` zal gradle automatisch gaan synchroniseren. Is dit niet het geval, kan je het handmatig door het in gradle menu (standaard gedocked aan de rechterkant van je Intellij window) het synchroniseren te starten.  

*Geeft gradle een synchronisatie error? Probeer er zelf uit te komen of kopieër de `build.gradle` uit de repositiory, in de folder `part1`.*


## LambdaHandler
--- 
In de file `LambdaHandler.kt` gaan we een classen optikken met een constructor voor de `AWS RequestHandler`. Gebruik de juiste import  `com.amazonaws.serverless.proxy.spark.SparkLambdaContainerHandler`.  

De `!isInitialized` check is hier van belang, aangezien de server wellicht blijft draaien na de eerste aanroep, wil je niet bij ieder request je routes defineren. Dat kost CPU tijd/MEM en dat kost *geld* :) Om deze reden definiëren binnen `defineRoutes` ook de errorHandler van AWS-Log4j

```kotlin
class LambdaHandler @Throws(ContainerInitializationException::class)
constructor(): RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private val handler = SparkLambdaContainerHandler.getAwsProxyHandler()
    private var initialized = false
    private val log = LoggerFactory.getLogger(LambdaHandler::class.java)

    override fun handleRequest(req: AwsProxyRequest, ctx: Context?): AwsProxyResponse {
        BasicConfigurator.configure()
        if(!initialized) {
            defineRoutes()
            initialized = true
        }
        return handler.proxy(req, ctx)
    }

    private fun defineRoutes() {
        initExceptionHandler{ e ->
            log.error("Spark init failure", e)
            System.exit(100)
        }
        get("/hello"){ _, _ ->
            "hello world!"
        }
    }
}

```

__Kotlin features__ :  
Enkele Kotlin features in het kort omschreven:    
`_` als parameter naam bij de functie `get("/hello"){_,_ -> ...}` betekent dat je de  parameter niet nodig hebt en weg wil laten. (weet niet precies wat dit inhoud)   
`val` => een constante. (een final variabele)   
`var` => een variabele.   
Kotlin method signature : `fun <naam>() : returnType`   
Inferred types : er staan geen type achter `private var initialized = false` omdat het type van `false` inferred wordt als een `boolean`. Expliciet type annoteren hoeft alleen als de compiler door complexiteit er niet uit komt. (of voor de mensen achter de knoppen, maar conventieel wordt het weggelaten tenzij het echt nodig is voor readability)

## AWS Lambda
------
Nu gaan we een server instellen op AWS. Stappenplan: 

1. Genereer shadowJar met `./gradlew shadowJar`
2. Inloggen bij [AWS console]
3. Zoek naar en selecteer `Lambda`
4. Druk op `Create function` 
    * Kies `Author from scratch`
    * Name: `SparkingLambda`
    * Runtime: `Java 8`
    * Choose existing role: `lambda_basic_execution`
5. Onder `Upload zip or JAR` uploaden we de gegenereerde `JAR` uit stap `1` met de suffix `-all` (bv. `{project_root}/build/libs/SparkingLambda-1.0-all.jar`)
    * Zet `runtime` op `Java 8`  
    * Geef het entrypoint van de handler in; bestaand uit de `<packageNaam>.<classNaam>`     bijvoorbeeld `handler` => `com.ordina.workshop.sparkinglambda.LambdaHandler`
6. Druk op `save`

![aws_select_lambda]
![aws_create_lambda_function_scratch]
![aws_upload_lambda_and_handler]

Nu hebben een Lambda hebben aangemaakt moeten we er ook voor zorgen dat we er wat mee kunnen doen. 
Dit doen we door een trigger toe te voegen; een `AWS API Gateway`.

## API Gateway
___

1. Ga terug naar het AWS home scherm en zoek naar en selecteer `API Gateway`
2. Create API
    * check `Configure as proxy resource`
    * Name: `{proxy+}`
    * Path: `{proxy+}`
    * Druk `create`
3. Selecteer nu de `any` method onder de resource en verwijder deze met het `actions` menu.
4. Maak via get `Actions` menu een nieuwe `Method` aan; selecteer `GET`
    * Integration type: `Lambda Function Proxy`
    * Lambda Function: `SparkingLambda`
    * Druk op `save`
5. Druk nu op __`test`__
    * Vul onder path: `/hello` in
    * Druk op test onderaan de pagina
    * links zie je nu `'Hello world!'` in de console
6. Gaat er wat mis? Dankzij SLF4J-simple komt de error log in `AWS Cloudwatch` terrecht. Cloudwatch vind je ook in de grote grabbelton op de hoofdpagina, middels het zoekveld.
![aws_create_api]
![aws_api_create_resource]
![aws_api_create_get]
![aws_api_get_test]

## 2. Calculator
---

Nu gaan we het iets spannender maken door een calculator in AWS lambda te zetten.  
Hierbij maken we gebruik van Post requests met een method Body.

Vul `LambdaHandler.kt` aan met het volgende
```kotlin
private fun defineRoutes() {
        ...
        post("/calc/sum") { req, resp -> CalculatorLambda.sumRequest(req, resp) }
    }
```
Voeg boven de LambdaHandler de volgende code toe:
```kotlin
data class CalcRequestObject(val numbers:Collection<Double>)
data class CalcResponseObject(val result:Double)

internal class CalculatorLambda {
    companion object {

        internal fun sumRequest(req: Request, response: Response): String {
            val mapper = jacksonObjectMapper()
            val calcReq = mapper.readValue<CalcRequestObject>(req.body())
            if(calcReq.numbers.count() > 0) {
                val sum = calcReq.numbers.reduce({sum: Double, element:Double -> sum + element});
                return mapper.writeValueAsString(CalcResponseObject(sum))
            }
            return "Super generic error message about the numbers' count"
        }
    }
}
```

Kotlin features:
- data class: een __`immutable`__ object met een waarde. Enigsins anoloog met een `struct`. Copy by value i.p.v. copy by reference. [dataclass documentatie]
- reduce: `Kotlin` beschikt over een groot aantal High Order functions zoals `listOf` en addities op collections zoals `map`, `flatMap`, `reduce`, etc. Neem eens een kijkje op [kotlin HOF en Lambda] 
- companion object: kort door de bocht -> hier zet je de Class specifieke methods e.d. in; de `static` zaken dus.

Met de `Jackson` library kan je met weinig code een `JSON string` omzetten naar een eenvoudige `data class`. zo representeerd de definitie `data class CalcRequestObject(val numbers:Collection<Double>)` onderstaande JSON string 
```json
{
    "numbers": [1.0,2.0,3.0,4.0,5.0]
}
```

Nu gaan we weer terug naar het [AWS console].  
Maak opnieuw een shadowJar van je code d.m.m. `./gradlew shadowJar` en upload deze naar de eerder aangemaakt AWS Lambda.
Voeg nu op deze wijze een `POST` method toe aan de `API gateway` zoals je de `GET` hebt toegevoegd in deel 1 om de nieuwe `calc/sum` functie te testen. Na het aanmaken van de `POST` method kan je deze weer testen door op `Test` te drukken. Vul als path `calc/sum` in met bovenstaande JSON als `Request body`.

![aws_gateway_test_post_sum]

### Uitdaging
___

Omdat in AWS Lambda CPU Time en Memory cruciaal zijn voor de kosten moeten we ervoor zorgen dat de code zo compact mogelijk is.   
Probeer zo compact mogelijk de functies voor het aftrekken, delen, en vermenigvuldigen te schrijven!
   
## 3. Testen
___
### _Vanaf dit punt wordt gebruik gemaakt van code geschreven als voorbeeld voor de uitdaging onderaan hoofdstuk twee. Deze code is te vinden in de directory part2_calculator_

Het testen van een Lambda bestaat uit mogelijk drie stappen, namelijk:
1. Het handmatig testen zoals we tot nu toe gedaan hebben
2. Unit tests om de verwerking van de lambda request te testen
3. Lokaal de daadwerkelijk AWS Lambda testen met `SAM CLI`

### 3.1 Spek
Spek is een framework voor het `BDD` testen van `Kotlin` code. Officieus is het ontwikkeld door medewerkers van `Intellij` en de integratie met `IDEA` is daarom uitstekend. Spek v1 is echter nog niet geschikt voor gebruik in het bedrijfsleven. Versie 2 belooft echter veel goeds! Het gebruik van Spek in deze workshop is daarom te zien als een kennismaking met framework.

Om Spek te integreren in ons project voegen we onderstaande toe aan `build.gradle`
```gradle
buildscript: {
    ...
    dependencies: {
        ...
        classpath 'org.junit.platform:junit-platform-gradle-plugin:1.0.0'
    }
}
...
apply plugin: 'org.junit.platform.gradle.plugin'
...
junitPlatform {
    filters {
        engines {
            include 'spek'
        }
    }
}
...
repositories {
    ...
    maven { url "http://dl.bintray.com/jetbrains/spek" }
}
...
dependencies: {
    ...
    testCompile group: 'junit', name: 'junit', version: '4.12'
    testCompile 'org.jetbrains.spek:spek-api:1.1.5'
    testRuntime 'org.jetbrains.spek:spek-junit-platform-engine:1.1.5'
    testCompile "org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version"
}

```

Installeer ook de `Spek` plugin voor Intellij door te navigeren naar `Preferences -> plugins` en te zoeken naar Spek.   


![spek_plugin_install]

Om te beginnen met het testen van Spek maken we de file `LambdaSpec.kt` aan in package `com.ordina.workshop.` in de test source map. Voeg hier onderstaande code aan toe.

_Kan jij de overige test functies schrijven voor het optellen, aftrekken en vermenigvuldigen?_

```kotlin
package com.ordina.workshop.sparkinglambda

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.math.RoundingMode
import kotlin.test.assertEquals

class LambdaSpec: Spek({
    given("A calculator with 4 functions") {
        ...
        on("function `div` with input 1,2,3,4") {
            val div = arrayListOf(1.0,2.0,3.0,4.0).reduce(CalculatorLambda.Companion::divider).toBigDecimal().setScale(4, RoundingMode.HALF_UP).toDouble()
            it("will output .0417") {
                assertEquals(div, 0.0417, "Div should be 0.0417 but is $div")
            }
        }
    }
})
```
Na het runnen van Spek zal je net als bij JUnit onderin de resultaten zien

![spek_plugin_result]

### 3.2 SAM CLI
Het lokaal testen van de gehele GATEWAY/Lambda service kan met de [SAM CLI] van AWS.
Zie [installeren van SAM CLI] voor de instructies.

#### 3.2.1 Configuratie
Om SAM te informeren wat we willen doen moeten we een configuratie bestand aanmaken. Maak het bestand volgende aan `{project_root}/src/resources/sam/config.yaml`

De inhoud van dit bestand ziet er uit als volgt. Let hierbij op dat de handler en CodeUri verwijzen naar de `handler` en de gegenereerde `jar`.   

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Resources:
  ExampleJavaFunction:
    Type: AWS::Serverless::Function
    Properties:
      Handler: com.ordina.workshop.sparkinglambda.LambdaHandler
      CodeUri: ../../../build/libs/SparkingLambda-1.0-all.jar
      Timeout: 30
      Runtime: java8
      Events:
        Sum:
          Type: Api
          Properties:
            Path: /calc/sum
            Method: post
        Multiplication:
          Type: Api
          Properties:
            Path: /calc/multi
            Method: post
        Subtraction:
          Type: Api
          Properties:
            Path: /calc/sub
            Method: post
        Division:
          Type: Api
          Properties:
            Path: /calc/div
            Method: post
```
#### 3.2.2 Opstarten SAM
Door in de terminal te navigeren naar de folder waar je bovenstaande `yaml` hebt geplaatst kan je `SAM` starten door het commando `'sam local start-api'` uit te voeren. Als alles goed gaat ziet de output ziet er als volgt uit:
```bash
$ sam local start-api
2018-08-05 13:57:10 Mounting ExampleJavaFunction at http://127.0.0.1:3000/calc/div [POST]
2018-08-05 13:57:10 Mounting ExampleJavaFunction at http://127.0.0.1:3000/calc/sum [POST]
2018-08-05 13:57:10 Mounting ExampleJavaFunction at http://127.0.0.1:3000/calc/multi [POST]
2018-08-05 13:57:10 Mounting ExampleJavaFunction at http://127.0.0.1:3000/calc/sub [POST]
2018-08-05 13:57:10 You can now browse to the above endpoints to invoke your functions. You do not need to restart/reload SAM CLI while working on your functions changes will be reflected instantly/automatically. You only need to restart SAM CLI if you update your AWS SAM template
2018-08-05 13:57:10  * Running on http://127.0.0.1:3000/ (Press CTRL+C to quit)
```

De Lambda kan je nu testen door met je favoriete programma of commando een POST te versturen naar de endpoints voor ons gedefineerd door `SAM`. Het `cURL` commando luidt als volgt:

```bash
curl --request POST \
  --url http://127.0.0.1:3000/calc/sum \
  --header 'Accept: application/json' \
  --header 'Cache-Control: no-cache' \
  --header 'Content-Type: application/json' \
  --data '{"numbers": [1.0,2.0,3.0]}'
```

![sam_test_result]

## Reference
___


[LinkedIn] learning die ik gevolgd heb, zeer uitgebreid over AWS account en code.

[spek]:https://spekframework.org/
[spark]:http://sparkjava.com/
[Docker]:https://docs.docker.com/
[AWS console]:https://console.aws.amazon.com
[kotlin HOF en Lambda]:https://kotlinlang.org/docs/reference/lambdas.html
[dataclass documentatie]:https://kotlinlang.org/docs/reference/data-classes.html
[LinkedIn]:https://www.linkedin.com/learning/developing-aws-lambda-functions-with-kotlin/intellij-maven-and-kotlin
[SAM CLI]:https://docs.aws.amazon.com/lambda/latest/dg/test-sam-cli.html
[installeren van SAM CLI]:https://docs.aws.amazon.com/lambda/latest/dg/sam-cli-requirements.html
[Pip]: https://pip.pypa.io/en/stable/installing/

![giphy][Remaining dependenies]

[Remaining dependenies]: https://media.giphy.com/media/l41lUjUgLLwWrz20w/giphy.gif


[aws_select_lambda]:./screenshots/ss_aws_select_lambda.png
[aws_create_lambda_function_scratch]:./screenshots/ss_aws_create_lambda_function_scratch.png
[aws_upload_lambda_and_handler]:./screenshots/ss_aws_upload_lambda_and_handler.png
[aws_create_api]:./screenshots/ss_aws_create_api.png
[aws_api_create_resource]:./screenshots/ss_aws_api_create_resource.png
[aws_api_create_get]:./screenshots/ss_aws_api_create_get.png
[aws_api_get_test]:./screenshots/ss_aws_api_get_test.png
[aws_gateway_test_post_sum]:./screenshots/ss_aws_api_post_sum_test.png
[spek_plugin_install]:./screenshots/ss_spek_plugin_install.png
[spek_plugin_result]:./screenshots/ss_spek_plugin_result.png
[sam_test_result]:./screenshots/ss_sam_test_result.png
