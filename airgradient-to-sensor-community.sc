#!/usr/bin/env -S scala-cli shebang

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright © 2024 Florian Schmaus

//> using scala "3.4.1"
//> using jvm "system"
//> using dep com.lihaoyi::upickle:3.3.0
//> using dep com.softwaremill.sttp.client4::core:4.0.0-M11
//> using dep dev.dirs:directories:26
//> using dep org.virtuslab::scala-yaml:0.0.8

import org.virtuslab.yaml.*
import sttp.client4.quick.*

val debug = if args.length == 1 && args(0) == "-d" then true else false

val xdgDirectories = dev.dirs.ProjectDirectories.from(
  "airgradient-to-sensor-community.geekplace.eu",
  "Geekplace",
  "AG2SC",
)

val configDir: String = xdgDirectories.configDir
val configFile = java.nio.file.Path.of(configDir, "config.yml")
if debug then println(f"config file: $configFile")

val configFileContent = java.nio.file.Files.readString(configFile)

case class Configuration(airgradientHost: String, sensorCommunitySensorUid: String) derives YamlCodec

val config = configFileContent.as[Configuration].toOption.get
if debug then println(f"config: $config")

val mapping = Map(
  "atmp" -> (7, "temperature"),
  "rhum" -> (7, "humidity"),
  "pm02" -> (1, "P2"),
  "pm01" -> (1, "P0"),
  "pm10" -> (1, "P10"),
  "pm003Count" -> (1, "N03"),
  "rco2" -> (1, "co2_ppm"),
)

def airgradientToSensorCommunity(airgradientHost: String, sensorCommunitySensorUid: String): Unit =
  val airgradientResponse = quickRequest
    .get(uri"http://${airgradientHost}/measures/current")
    .send()

  if debug then println(airgradientResponse)

  val json = ujson.read(airgradientResponse.body)

  val mapResult = for
    (from, (pin, to)) <- mapping
    value = json(from)
  yield (pin, to, value)

  val res = mapResult
    .groupBy(_._1)
    .mapValues(
      _.map(v =>
        ujson.Obj(
          "value_type" -> v._2,
          "value" -> v._3,
        )
      )
    )

  for (pin, sensordatavalues) <- res
  do
    val sensorsCommunityJson = ujson.Obj(
      "software_version" -> json("firmwareVersion"),
      "sensordatavalues" -> sensordatavalues,
    )

    val body = sensorsCommunityJson.toString

    if debug then println(body)

    val response = basicRequest
      .contentType("application/json")
      .header("X-Sensor", sensorCommunitySensorUid)
      .header("X-Pin", pin.toString)
      .body(body)
      .post(uri"https://api.sensor.community/v1/push-sensor-data/")
      .send()

    if debug then println(response)

while true do
  if debug then println(f"${java.time.LocalTime.now()}: pulling and pushing data")
  airgradientToSensorCommunity(config.airgradientHost, config.sensorCommunitySensorUid)
  Thread.sleep(java.time.Duration.ofSeconds(97))
