package demo

import scalanative.native._
import scalanative.libc.stdlib._
import scala.math.sqrt

@struct
class Point(val x: Double = 0, val y: Double = 0) {
  @inline def /(k: Double): Point = new Point(x / k, y / k)

  @inline def +(p: Point) = new Point(x + p.x, y + p.y)

  @inline def -(p: Point) = new Point(x - p.x, y - p.y)

  @inline private def sq(x: Double) = x * x

  @inline def modulus = sqrt(sq(x) + sq(y))
}

/*
@extern object Jansson {

  @extern class json_type
  /*typedef enum {
      JSON_OBJECT,
      JSON_ARRAY,
      JSON_STRING,
      JSON_INTEGER,
      JSON_REAL,
      JSON_TRUE,
      JSON_FALSE,
      JSON_NULL
  } json_type;*/


  @struct
  class json_t(val typ: json_type = null,val refcount: Int = 0)

  @struct
  class json_error_t(val line: Int = 0,
    val column: Int = 0,
    val position: Int = 0,
    val source: Ptr[Char] = null,
    val text: Ptr[Char] = null)

  def json_load_file(path: Ptr[Byte], flags: Int, error: Ptr[json_error_t]): Ptr[json_t] = extern
}
import Jansson._
*/

object Main {
  final val n = 10
  final val iters = 15

  def dist(x: Point, y: Point) = (x - y).modulus

  def closest(p: Point, choices: Ptr[Point], choicesSize: Int): Int = {
    var i = 1
    var minDist = dist(p, choices(0))
    val ret = malloc(sizeof[Int]).cast[Ptr[Int]]
    !ret = 0
    var actDist = minDist
    while (i < choicesSize) {
      actDist = dist(p, choices(i))
      if (actDist < minDist) {
        minDist = actDist
        !ret = i
      }
      i += 1
    }
    !ret
  }

  def average(xs: Ptr[Point], size: Int): Point = {
    val point = stackalloc[Point]
    !point = new Point(0,0)
    var i = 0
    while (i < size) {
      !point = !point + xs(i)
      i += 1
    }
    !point = !point / size.toDouble
    !point
  }

  def clusters(xs: Ptr[Point], centroids: Ptr[Point], xsSize: Int, centroidsSize: Int) = {
    var i = 0
    val cn = malloc(sizeof[Int] * centroidsSize).cast[Ptr[Int]]
    val xsDists = malloc(sizeof[Int] * xsSize).cast[Ptr[Int]]
    val newCentroids = malloc(sizeof[Point] * centroidsSize).cast[Ptr[Point]]
    
    while (i < xsSize) {
      xsDists(i) = closest(xs(i), centroids, centroidsSize)
      i += 1
    }

    i = 0
    while (i < centroidsSize) {
      newCentroids(i) = new Point(0,0)
      cn(i) = 0
      i += 1
    }
    i = 0
    while (i < xsSize) {
      newCentroids(xsDists(i)) = newCentroids(xsDists(i)) + xs(i)
      cn(xsDists(i)) = cn(xsDists(i)) + 1
      i += 1
    }
    i = 0
    while (i < centroidsSize) {
      newCentroids(i) = newCentroids(i) / cn(i).toDouble
      i += 1
    }

    newCentroids
  }

  def main(args: Array[String]): Unit = {

    val points = malloc(sizeof[Point] * 10).cast[Ptr[Point]]
    var i = 0
    while( i < 10) {
      points(i) = new Point(i,i)
      i += 1
    }

    var centroids = malloc(sizeof[Point] * 2).cast[Ptr[Point]]
    
    i = 0
    while( i < 2) {
      centroids(i) = new Point(points(i).x, points(i).y)
      i += 1
    }

    fprintf(stdout, c"init\n")

    i = 0
    while( i < 10 ) {
      centroids = clusters(points, centroids, 10, 2)
      i+=1
    }

    val res = centroids
    
    fprintf(stdout, c"C0 x %f  ", res(0).x)
    fprintf(stdout, c"   y %f\n", res(0).y)
    fprintf(stdout, c"C1 x %f  ", res(1).x)
    fprintf(stdout, c"   y %f\n", res(1).y)

    fprintf(stdout, c"end\n")

/*
    var error = malloc(sizeof[json_error_t]).cast[Ptr[json_error_t]]
    
    var json = json_load_file(c"/home/andrea/workspace/kmeans/points.json", 0, error)

    fprintf(stdout, c"does it crash? %d", json)
*/
  }
}
