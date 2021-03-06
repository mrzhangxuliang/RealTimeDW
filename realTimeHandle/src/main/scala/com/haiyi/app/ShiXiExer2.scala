package com.haiyi.app

import java.text.SimpleDateFormat

import org.apache.flink.api.common.state.{ListState, ListStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.scala.typeutils.Types
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

/**
 * @author Mr.Xu
 * @create 2020-08-18
 *
 */
object ShiXiExer2 {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    val inputStream = env.fromElements("D0000001,2020040201,220",
      "D0000001,2020040202,220",
      "D0000001,2020040203,230",
      "D0000001,2020040204,220",
      "D0000001,2020040205,230",
      "D0000001,2020040206,230",
      "D0000001,2020040207,270",
      "D0000001,2020040208,270",
      "D0000001,2020040209,270",
      "D0000001,2020040210,270",
      "D0000001,2020040211,220",
      "D0000001,2020040212,",
      ",,",
      "D0000001,2020040214,290",
      "D0000001,2020040215,220",
      "D0000002,2020040201,220",
      "D0000002,2020040202,220")
        .filter(line =>{
          val words = line.split(",")
          if(words.size == 3 && words(2).toLong != 220){
            true
          }else{
            false
          }
        })
        .map(line =>{
          val words = line.split(",")
          (words(0), words(1), words(2))
        })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[(String, String, String)](Time.seconds(0)) {
        override def extractTimestamp(element: (String, String, String)): Long = {
          val simpleDateFormat = new SimpleDateFormat("yyyyMMddHH")
          val ts = simpleDateFormat.parse(element._2).getTime
          ts
        }
      })
        .keyBy(_._1)
        .process(new MyKeyProcess)
        .print()

    env.execute("ShiXiExer")
  }

  class MyKeyProcess extends KeyedProcessFunction[String, (String, String, String),String]{
    //  保存违法数据
    lazy val errorCodeState: ListState[(String, String, String)] = getRuntimeContext.getListState(
      new ListStateDescriptor[(String, String, String)]("errorCode", Types.of[(String, String, String)])
    )
    //  保存上一条非法数据
    lazy val lastTemp: ValueState[(String, String, String)] = getRuntimeContext.getState(
      new ValueStateDescriptor[(String, String, String)]("lastTemp", Types.of[(String, String, String)])
    )

    override def processElement(value: (String, String, String),
                                ctx: KeyedProcessFunction[String, (String, String, String), String]#Context,
                                out: Collector[String]): Unit = {

      val last = lastTemp.value()
      lastTemp.update(value)

      if(last == null){
        errorCodeState.add(value)
      }else if( value._2.toLong - last._2.toLong == 1){
        errorCodeState.add(value)
      }else{
        errorCodeState.clear()
        errorCodeState.add(value)
      }

      import scala.collection.JavaConversions._

      if (errorCodeState.get().iterator().size >= 3) {
        out.collect(errorCodeState.get().toString())
      }
    }
  }
}
