package de.johoop.xplane.util

package object math {
  case class Vector3d(x: Double, y: Double, z: Double)

  def ramerDouglasPeucker(vs: Vector[Vector3d], epsilon: Double): Vector[Vector3d] = {
    assert(vs.size >= 2, "This algorithm requires at least two points as input.")

    val a1 = vs.head
    val a2 = vs.last
    val u = diff(a2, a1)

    val others = vs.tail.init
    if (others.isEmpty) Vector(a1, a2) else {
      val distances = others map (distanceFromLine(_, a1, u))
      val maxDistance = distances.max
    }

    // TODO find the maximum and split there, recurse
    // TODO or simply eliminate if < epsilon
  }

  /** Distance from a point to a line in 3-dimensional orthonormal space
    *
    * @param p Point to compute the distance from.
    * @param a Point through which the line goes.
    * @param u Vector which determines the direction of the line.
    *
    * @return Distance from the given point to the given line.
    */
  def distanceFromLine(p: Vector3d, a: Vector3d, u: Vector3d): Double =
    norm(crossProduct(diff(p, a), u)) / norm(u)

  def norm(v: Vector3d): Double =
    Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

  def crossProduct(v1: Vector3d, v2: Vector3d) =
    Vector3d(
      v1.y * v2.z - v1.z * v2.y,
      v1.z * v2.x - v1.x * v2.z,
      v1.x * v2.y - v1.y * v2.x
    )

  def diff(v1: Vector3d, v2: Vector3d): Vector3d =
    Vector3d(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z)
}
