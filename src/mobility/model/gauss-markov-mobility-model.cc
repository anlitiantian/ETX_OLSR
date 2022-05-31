/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/*
 * Copyright (c) 2009 Dan Broyles
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation;
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Author: Dan Broyles <dbroyl01@ku.edu>
 */
#include <cmath>
#include "ns3/simulator.h"
#include "ns3/double.h"
#include "ns3/pointer.h"
#include "ns3/string.h"
#include "gauss-markov-mobility-model.h"
#include "position-allocator.h"

namespace ns3 {

NS_OBJECT_ENSURE_REGISTERED (GaussMarkovMobilityModel);

TypeId
GaussMarkovMobilityModel::GetTypeId (void)
{
  static TypeId tid = TypeId ("ns3::GaussMarkovMobilityModel")
    .SetParent<MobilityModel> ()
    .SetGroupName ("Mobility")
    .AddConstructor<GaussMarkovMobilityModel> ()
    .AddAttribute ("Bounds",
                   "Bounds of the area to cruise.",
                   BoxValue (Box (-100.0, 100.0, -100.0, 100.0, 0.0, 100.0)),
                   MakeBoxAccessor (&GaussMarkovMobilityModel::m_bounds),
                   MakeBoxChecker ())
    .AddAttribute ("TimeStep",
                   "Change current direction and speed after moving for this time.",
                   TimeValue (Seconds (1.0)),
                   MakeTimeAccessor (&GaussMarkovMobilityModel::m_timeStep),
                   MakeTimeChecker ())
    .AddAttribute ("Alpha",
                   "A constant representing the tunable parameter in the Gauss-Markov model.",
                   DoubleValue (1.0),
                   MakeDoubleAccessor (&GaussMarkovMobilityModel::m_alpha),
                   MakeDoubleChecker<double> ())
    .AddAttribute ("MeanVelocity",
                   "A random variable used to assign the average velocity.",
                   StringValue ("ns3::UniformRandomVariable[Min=0.0|Max=1.0]"),
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_rndMeanVelocity),
                   MakePointerChecker<RandomVariableStream> ())
    .AddAttribute ("MeanDirection",
                   "A random variable used to assign the average direction.",
                   StringValue ("ns3::UniformRandomVariable[Min=0.0|Max=6.283185307]"),
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_rndMeanDirection),
                   MakePointerChecker<RandomVariableStream> ())
    .AddAttribute ("MeanPitch",
                   "A random variable used to assign the average pitch.",
                   StringValue ("ns3::UniformRandomVariable[Min=-0.1|Max=0.1]"),
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_rndMeanPitch),
                   MakePointerChecker<RandomVariableStream> ())
    .AddAttribute ("NormalVelocity",
                   "A gaussian random variable used to calculate the next velocity value.",
                   StringValue ("ns3::NormalRandomVariable[Mean=0.0|Variance=1.0|Bound=10.0]"), // Defaults to zero mean, and std dev = 1, and bound to +-10 of the mean
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_normalVelocity),
                   MakePointerChecker<NormalRandomVariable> ())
    .AddAttribute ("NormalDirection",
                   "A gaussian random variable used to calculate the next direction value.",
                   StringValue ("ns3::NormalRandomVariable[Mean=0.0|Variance=1.0|Bound=10.0]"),
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_normalDirection),
                   MakePointerChecker<NormalRandomVariable> ())
    .AddAttribute ("NormalPitch",
                   "A gaussian random variable used to calculate the next pitch value.",
                   StringValue ("ns3::NormalRandomVariable[Mean=0.0|Variance=1.0|Bound=10.0]"),
                   MakePointerAccessor (&GaussMarkovMobilityModel::m_normalPitch),
                   MakePointerChecker<NormalRandomVariable> ())
	.AddAttribute("MaxAngularVelocity",
					"An angular velocity set manually.",
					DoubleValue (1.5),
					MakeDoubleAccessor (&GaussMarkovMobilityModel::m_Wmax),
					MakeDoubleChecker<double> ());

  return tid;
}

GaussMarkovMobilityModel::GaussMarkovMobilityModel ()
{
  m_meanVelocity = 0.0;
  m_meanDirection = 0.0;
  m_meanPitch = 0.0;
  m_centerPosition.x = 0;
  m_centerPosition.y = 0;
  m_isCircle = false;

  m_distance_margin = 200.0;

  m_event = Simulator::ScheduleNow (&GaussMarkovMobilityModel::Start, this);
  m_helper.Unpause ();
}

void
GaussMarkovMobilityModel::Start (void)
{
  if (m_meanVelocity == 0.0)
    {
      //Initialize the mean velocity, direction, and pitch variables
      m_meanVelocity = m_rndMeanVelocity->GetValue ();
      m_meanDirection = m_rndMeanDirection->GetValue ();
      m_meanPitch = m_rndMeanPitch->GetValue ();
      double cosD = std::cos (m_meanDirection);
      double cosP = std::cos (m_meanPitch);
      double sinD = std::sin (m_meanDirection);
      double sinP = std::sin (m_meanPitch);


      //Initialize the starting velocity, direction, and pitch to be identical to the mean ones
      m_Velocity = m_meanVelocity;
      m_Direction = m_meanDirection;
      m_Pitch = m_meanPitch;
      //Set the velocity vector to give to the constant velocity helper
      m_helper.SetVelocity (Vector (m_Velocity*cosD*cosP, m_Velocity*sinD*cosP, m_Velocity*sinP));
    }
  if(!m_isCircle){
	  m_helper.Update ();																	//当上次的运动不是圆周运动，说明位置需要更新，否则无须更新
  }

  //Get the next values from the gaussian distributions for velocity, direction, and pitch
  double rv = m_normalVelocity->GetValue ();
  double rd = m_normalDirection->GetValue ();
  double rp = m_normalPitch->GetValue ();

  //Calculate the NEW velocity, direction, and pitch values using the Gauss-Markov formula:
  //newVal = alpha*oldVal + (1-alpha)*meanVal + sqrt(1-alpha^2)*rv
  //where rv is a random number from a normal (gaussian) distribution
  double one_minus_alpha = 1 - m_alpha;
  double sqrt_alpha = std::sqrt (1 - m_alpha*m_alpha);
  m_Velocity  = m_alpha * m_Velocity  + one_minus_alpha * m_meanVelocity  + sqrt_alpha * rv;
  m_Direction = m_alpha * m_Direction + one_minus_alpha * m_meanDirection + sqrt_alpha * rd;
  m_Pitch     = m_alpha * m_Pitch     + one_minus_alpha * m_meanPitch     + sqrt_alpha * rp;

  //保证角度始终在[0,2M_PI]范围内，暂时不关心俯仰角
  while(m_Direction < 0 || m_Direction > 2*M_PI){
	  if(m_Direction < 0) m_Direction += 2*M_PI;
	  if(m_Direction > 2*M_PI) m_Direction -= 2*M_PI;
  }

  //Calculate the linear velocity vector to give to the constant velocity helper
  double cosDir = std::cos (m_Direction);
  double cosPit = std::cos (m_Pitch);
  double sinDir = std::sin (m_Direction);
  double sinPit = std::sin (m_Pitch);
  double vx = m_Velocity * cosDir * cosPit;
  double vy = m_Velocity * sinDir * cosPit;
  double vz = m_Velocity * sinPit;
  m_helper.SetVelocity (Vector (vx, vy, vz));

  m_helper.Unpause ();

  DoWalk (m_timeStep);
}

void
GaussMarkovMobilityModel::DoWalk (Time delayLeft)
{
  m_helper.UpdateWithBounds (m_bounds);														//其中的update发生改变位置的操作
  Vector position = m_helper.GetCurrentPosition ();
  Vector speed = m_helper.GetVelocity ();
  Vector nextPosition = position;															//求即将要到达的位置，但不确定是否要到达
  nextPosition.x += speed.x * delayLeft.GetSeconds ();
  nextPosition.y += speed.y * delayLeft.GetSeconds ();
  nextPosition.z += speed.z * delayLeft.GetSeconds ();
  if (delayLeft.GetSeconds () < 0.0) delayLeft = Seconds (1.0);

  // Make sure that the position by the next time step is still within the boundary.
  // If out of bounds, then alter the velocity vector and average direction to keep the position in bounds
  if (isInMarginArea(nextPosition)){														//在小区域内，不用改变运动状态
	  m_centerPosition.x = 0;																//将圆心坐标置零
	  m_centerPosition.y = 0;
	  m_isCircle = false;
	  m_helper.Unpause ();
	  m_event = Simulator::Schedule (delayLeft, &GaussMarkovMobilityModel::Start, this);
  }else if (m_bounds.IsInside (nextPosition))												//在大区域内，做圆周运动
    {
	  //先求角速度w的正负
	  double sign = 1;
//	  if((nextPosition.x > m_bounds.xMax - m_distance_margin && m_Direction >= 1.5*M_PI && m_Direction <= 2*M_PI)
//			  || (nextPosition.x < m_bounds.xMin + m_distance_margin && m_Direction >= 0.5*M_PI && m_Direction <= M_PI)
//			  || (nextPosition.y > m_bounds.yMax - m_distance_margin && m_Direction >= 0 && m_Direction <= 0.5*M_PI)
//			  || (nextPosition.y < m_bounds.yMin + m_distance_margin && m_Direction >= M_PI && m_Direction <= 1.5*M_PI))
//		  sign = -1;
	  nextPosition = position;
	  m_Wmax = sign*m_Wmax;
	  double vxy = std::sqrt(speed.x*speed.x + speed.y*speed.y);
	  double r = -vxy/m_Wmax;
	  double a = -m_Wmax*delayLeft.GetSeconds();

	  //再求圆心坐标，存到对象中
	  if(m_centerPosition.x == 0 && m_centerPosition.y == 0){
		  double xc = position.x + r*std::sin(m_Direction);
		  double yc = position.y - r*std::cos(m_Direction);
		  m_centerPosition.x = xc;
		  m_centerPosition.y = yc;
	  }
	  nextPosition.x = m_centerPosition.x + r*std::sin(a - m_Direction);
	  nextPosition.y = m_centerPosition.y + r*std::cos(a - m_Direction);

	  if (nextPosition.z > m_bounds.zMax || nextPosition.z < m_bounds.zMin)
	  {
		speed.z = -speed.z;
		m_meanPitch = -m_meanPitch;
		nextPosition.z = position.z + speed.z * delayLeft.GetSeconds ();
	  }
	  m_meanDirection = m_Direction - a;													//下一个方向
	  m_Direction = m_meanDirection;
	  speed.x = vxy*std::cos(m_Direction);													//线速度大小保持不变
	  speed.y = vxy*std::sin(m_Direction);

	  m_helper.SetPosition(nextPosition);														//更新位置
	  m_helper.SetVelocity (speed);
	  m_helper.Unpause ();
	  m_isCircle = true;
	  m_event = Simulator::Schedule (delayLeft, &GaussMarkovMobilityModel::Start, this);
    }
  else
    {																						//超出外边界
      if (nextPosition.x > m_bounds.xMax || nextPosition.x < m_bounds.xMin) 
        {
          speed.x = -speed.x;
          m_meanDirection = M_PI - m_meanDirection;
        }

      if (nextPosition.y > m_bounds.yMax || nextPosition.y < m_bounds.yMin) 
        {
          speed.y = -speed.y;
          m_meanDirection = -m_meanDirection;
        }

      if (nextPosition.z > m_bounds.zMax || nextPosition.z < m_bounds.zMin) 
        {
          speed.z = -speed.z;
          m_meanPitch = -m_meanPitch;
        }

      m_Direction = m_meanDirection;
      m_Pitch = m_meanPitch;
      m_helper.SetVelocity (speed);
      m_helper.Unpause ();
      m_isCircle = false;
      m_event = Simulator::Schedule (delayLeft, &GaussMarkovMobilityModel::Start, this);
    }
  NotifyCourseChange ();
}

bool
GaussMarkovMobilityModel::isInMarginArea(const Vector &position)
{
	return
	    position.x <= m_bounds.xMax - m_distance_margin && position.x >= m_bounds.xMin + m_distance_margin &&
	    position.y <= m_bounds.yMax - m_distance_margin && position.y >= m_bounds.yMin + m_distance_margin &&
	    position.z <= m_bounds.zMax && position.z >= m_bounds.zMin;
}

void
GaussMarkovMobilityModel::DoDispose (void)
{
  // chain up
  MobilityModel::DoDispose ();
}

Vector
GaussMarkovMobilityModel::DoGetPosition (void) const
{
  m_helper.Update ();
  return m_helper.GetCurrentPosition ();
}
void 
GaussMarkovMobilityModel::DoSetPosition (const Vector &position)
{
  m_helper.SetPosition (position);
  m_event.Cancel ();
  m_event = Simulator::ScheduleNow (&GaussMarkovMobilityModel::Start, this);
}
Vector
GaussMarkovMobilityModel::DoGetVelocity (void) const
{
  return m_helper.GetVelocity ();
}

int64_t
GaussMarkovMobilityModel::DoAssignStreams (int64_t stream)
{
  m_rndMeanVelocity->SetStream (stream);
  m_normalVelocity->SetStream (stream + 1);
  m_rndMeanDirection->SetStream (stream + 2);
  m_normalDirection->SetStream (stream + 3);
  m_rndMeanPitch->SetStream (stream + 4);
  m_normalPitch->SetStream (stream + 5);
  return 6;
}

} // namespace ns3
