# 6347_miniproject03_teacher

The goal of this exercise is to develop a structured streaming spark application that reads particulate matter readings from Kafka, extract values from the the readings and then calculate aggregate statistics.

The excercise is based on data from the following study:
Liang, X., S. Li, S. Zhang, H. Huang, and S. X. Chen (2016), PM2.5 data reliability, consistency, and air quality assessment in five Chinese cities, J. Geophys. Res. Atmos., 121, 10220â€“10236.

You will do so by implementing the three methods below:
- ingestKafkaTopic
- extractValues
- calculateTopPollutionEventsPerWeek

You're done when all test cases pass.

The data will be streaming from five sources:
- US Post in Beijing
- US Post in Chengdu
- US Post in Guangzhou
- US Post in Shanghai
- US Post in Shenyang

The first reading is from 1/1/2010 at midnight,
the last reading is from 12/31/2015 at 11pm.

The key for each kafka record is the name of the source.
The value for each kafka record is a CSV string with the following columns:
- year, numeric year, e.g. 2015
- month, numeric month, January is 1 etc.
- day, numeric day, the first of the month is 1 etc.
- hour, numeric 24h hour, 11pm is 23 etc.
- season, ignore
- PM_US Post, a double, the particulate matter reading at the US Post for the source,
- DEWP, a double, the dew point
- HUMI, a double, the humidity
- PRES, a double, the pressure
- TEMP, a double, the temperature
- cbwd, a double, combined wind direction
- Iws, a double, cumulated wind speed
- precipitation, a double, hourly precipication in mm
- Iprec, a double, cumulated precipication

Example kafka record:
key: "BeijingPM20100101_20151231.csv"
value: "2010,1,1,0,4,null,-21.0,43.0,1021.0,-11.0,NW,1.79,0.0,0.0"

Good luck!
