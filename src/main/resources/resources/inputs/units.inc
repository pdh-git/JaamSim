" Input file for unit conversion

" Created February 9, 2011 by Stephen Wong

Define Palette { Units }

Define ObjectType  { TimeUnit
                     DistanceUnit
                     SpeedUnit
                     AccelerationUnit
                     MassUnit
                     MassFlowUnit
                     VolumeUnit
                     VolumeFlowUnit
                     AngleUnit
                     AngularSpeedUnit
                     EnergyUnit
                     EnergyDensityUnit
                     SpecificEnergyUnit
                     PowerUnit
                     CostUnit
                     CostRateUnit
                     LinearDensityUnit
					 DensityUnit
					 PressureUnit
					 ViscosityUnit }

TimeUnit            JavaClass { com.jaamsim.units.TimeUnit            } Palette { Units } DragAndDrop { FALSE }
DistanceUnit        JavaClass { com.jaamsim.units.DistanceUnit        } Palette { Units } DragAndDrop { FALSE }
SpeedUnit           JavaClass { com.jaamsim.units.SpeedUnit           } Palette { Units } DragAndDrop { FALSE }
AccelerationUnit    JavaClass { com.jaamsim.units.AccelerationUnit    } Palette { Units } DragAndDrop { FALSE }
MassUnit            JavaClass { com.jaamsim.units.MassUnit            } Palette { Units } DragAndDrop { FALSE }
MassFlowUnit        JavaClass { com.jaamsim.units.MassFlowUnit        } Palette { Units } DragAndDrop { FALSE }
VolumeUnit          JavaClass { com.jaamsim.units.VolumeUnit          } Palette { Units } DragAndDrop { FALSE }
VolumeFlowUnit      JavaClass { com.jaamsim.units.VolumeFlowUnit      } Palette { Units } DragAndDrop { FALSE }
AngleUnit           JavaClass { com.jaamsim.units.AngleUnit           } Palette { Units } DragAndDrop { FALSE }
AngularSpeedUnit    JavaClass { com.jaamsim.units.AngularSpeedUnit    } Palette { Units } DragAndDrop { FALSE }
EnergyUnit          JavaClass { com.jaamsim.units.EnergyUnit          } Palette { Units } DragAndDrop { FALSE }
EnergyDensityUnit   JavaClass { com.jaamsim.units.EnergyDensityUnit   } Palette { Units } DragAndDrop { FALSE }
SpecificEnergyUnit  JavaClass { com.jaamsim.units.SpecificEnergyUnit  } Palette { Units } DragAndDrop { FALSE }
PowerUnit           JavaClass { com.jaamsim.units.PowerUnit           } Palette { Units } DragAndDrop { FALSE }
CostUnit            JavaClass { com.jaamsim.units.CostUnit            } Palette { Units } DragAndDrop { FALSE }
CostRateUnit        JavaClass { com.jaamsim.units.CostRateUnit        } Palette { Units } DragAndDrop { FALSE }
LinearDensityUnit   JavaClass { com.jaamsim.units.LinearDensityUnit   } Palette { Units } DragAndDrop { FALSE }
DensityUnit         JavaClass { com.jaamsim.units.DensityUnit         } Palette { Units } DragAndDrop { FALSE }
PressureUnit        JavaClass { com.jaamsim.units.PressureUnit        } Palette { Units } DragAndDrop { FALSE }
ViscosityUnit       JavaClass { com.jaamsim.units.ViscosityUnit       } Palette { Units } DragAndDrop { FALSE }

" ======================================================================================================================================================
" Time units
" ======================================================================================================================================================

Define TimeUnit       { Second Minute Hour Day Week Year }

Second ConversionFactorToSI { 1        } Alias { Seconds seconds second Secs secs Sec sec s }
Minute ConversionFactorToSI { 60       } Alias { Minutes minutes minute Mins mins Min min }
Hour   ConversionFactorToSI { 3600     } Alias { Hours hours hour Hrs hrs Hr hr h }
Day    ConversionFactorToSI { 86400    } Alias { Days  days  day                d }
Week   ConversionFactorToSI { 604800   } Alias { Weeks weeks week               w }
Year   ConversionFactorToSI { 31536000 } Alias { Years years year Yrs yrs Yr yr y }

" ======================================================================================================================================================
" Distance units
" ======================================================================================================================================================

Define DistanceUnit   { Metres Kilometres nmi Miles Feet Inches mm }

Metres       ConversionFactorToSI { 1        } Alias { Meters m }
Kilometres   ConversionFactorToSI { 1000     } Alias { Kilometers km }
nmi          ConversionFactorToSI { 1852     }
Miles        ConversionFactorToSI { 1609.344 } Alias { mi }
Feet         ConversionFactorToSI { 0.3048   } Alias { ft }
Inches       ConversionFactorToSI { 0.0254   } Alias { in }
mm           ConversionFactorToSI { 0.001    }

" ======================================================================================================================================================
" Speed units
" ======================================================================================================================================================

Define SpeedUnit      { m/s km/h Knots mph }

m/s          ConversionFactorToSI { 1             }
km/h         ConversionFactorToSI { 1000 3600     } Alias { kmph }
Knots        ConversionFactorToSI { 1852 3600     } Alias { knots kn }
mph          ConversionFactorToSI { 1609.344 3600 } Alias { mi/h } "Miles per hour

" ======================================================================================================================================================
" Acceleration units
" ======================================================================================================================================================

Define AccelerationUnit      { m/s2 ft/s2 }

m/s2         ConversionFactorToSI { 1      } Alias { m/s^2 }
ft/s2        ConversionFactorToSI { 0.3048 } Alias { ft/s^2 }

" ======================================================================================================================================================
" Mass units
" ======================================================================================================================================================

Define MassUnit       { Kilograms Tonnes kt Mt }

Kilograms ConversionFactorToSI { 1          } Alias { kg }
Tonnes    ConversionFactorToSI { 1000       } Alias { t tonne tonnes }
kt        ConversionFactorToSI { 1000000    }    
Mt        ConversionFactorToSI { 1000000000 }

" ======================================================================================================================================================
" Mass flow units
" ======================================================================================================================================================

Define MassFlowUnit   { kg/h kg/d kg/y t/h t/d t/y kt/h kt/d kt/y Mt/h Mt/d Mt/y }

kg/h ConversionFactorToSI { 1           3600     }
kg/d ConversionFactorToSI { 1           86400    }
kg/y ConversionFactorToSI { 1           31536000 }

t/h  ConversionFactorToSI { 1000        3600     } Alias { tph }
t/d  ConversionFactorToSI { 1000        86400    }
t/y  ConversionFactorToSI { 1000        31536000 }

kt/h ConversionFactorToSI { 1000000     3600     } Alias { ktph }
kt/d ConversionFactorToSI { 1000000     86400    }
kt/y ConversionFactorToSI { 1000000     31536000 } Alias { ktpa }

Mt/h ConversionFactorToSI { 1000000000  3600     }
Mt/d ConversionFactorToSI { 1000000000  86400    }
Mt/y ConversionFactorToSI { 1000000000  31536000 } Alias { Mtpa }

" ======================================================================================================================================================
" Volume units 
" ======================================================================================================================================================

Define VolumeUnit     { m3 km3 bbl mbbl mmbbl }

m3    ConversionFactorToSI { 1                 } Alias { M3  M^3  m^3  }        " cubic meters
km3   ConversionFactorToSI { 1000              } Alias { KM3 KM^3 km^3 }        " thousands of cubic meters
bbl   ConversionFactorToSI { 1.58987294928E-01 } Alias { BBL }                  " barrels of oil
mbbl  ConversionFactorToSI { 1.58987294928E+02 } Alias { MBBL Mbbl mBBL kbbl }  " thousands of barrels of oil
mmbbl ConversionFactorToSI { 1.58987294928E+05 } Alias { MMBBL MMbbl mmBBL }    " millions of barrels of oil

" ======================================================================================================================================================
" Volume flow units
" ======================================================================================================================================================

Define VolumeFlowUnit { m3/s m3/h m3/d m3/y km3/h km3/d km3/y bbl/h bbl/d bbl/y mbbl/h mbbl/d mbbl/y mmbbl/h mmbbl/d mmbbl/y }

m3/s    ConversionFactorToSI { 1 }
m3/h    ConversionFactorToSI { 1                 3600     } Alias {    M3/h   M^3/h   m^3/h }
m3/d    ConversionFactorToSI { 1                 86400    } Alias {    M3/d   M^3/d   m^3/d }
m3/y    ConversionFactorToSI { 1                 31536000 } Alias {    M3/y   M^3/y   m^3/y }

km3/h   ConversionFactorToSI { 1000              3600     } Alias {   KM3/h  KM^3/h  km^3/h }
km3/d   ConversionFactorToSI { 1000              86400    } Alias {   KM3/d  KM^3/d  km^3/d }
km3/y   ConversionFactorToSI { 1000              31536000 } Alias {   KM3/y  KM^3/y  km^3/y }

bbl/h   ConversionFactorToSI { 1.58987294928E-01 3600     } Alias {   BBL/h }
bbl/d   ConversionFactorToSI { 1.58987294928E-01 86400    } Alias {   BBL/d }
bbl/y   ConversionFactorToSI { 1.58987294928E-01 31536000 } Alias {   BBL/y }

mbbl/h  ConversionFactorToSI { 1.58987294928E+02 3600     } Alias {  MBBL/h  Mbbl/h  mBBL/h kbbl/h }
mbbl/d  ConversionFactorToSI { 1.58987294928E+02 86400    } Alias {  MBBL/d  Mbbl/d  mBBL/d kbbl/d }
mbbl/y  ConversionFactorToSI { 1.58987294928E+02 31536000 } Alias {  MBBL/y  Mbbl/y  mBBL/y kbbl/y }

mmbbl/h ConversionFactorToSI { 1.58987294928E+05 3600     } Alias { MMBBL/h MMbbl/h mmBBL/h }
mmbbl/d ConversionFactorToSI { 1.58987294928E+05 86400    } Alias { MMBBL/d MMbbl/d mmBBL/d }
mmbbl/y ConversionFactorToSI { 1.58987294928E+05 31536000 } Alias { MMBBL/y MMbbl/y mmBBL/y }

" ======================================================================================================================================================
" Angle units
" ======================================================================================================================================================

Define AngleUnit      { Radians Degrees }

Degrees ConversionFactorToSI { 3.14159265358979 180 } Alias { deg }
Radians ConversionFactorToSI { 1                    } Alias { rad }

" ======================================================================================================================================================
" Anglular speed units
" ======================================================================================================================================================

Define AngularSpeedUnit { rad/s rad/h deg/s deg/h rpm }

rad/s ConversionFactorToSI { 1                       }
rad/h ConversionFactorToSI { 1                3600   }
deg/s ConversionFactorToSI { 3.14159265358979 180    }
deg/h ConversionFactorToSI { 3.14159265358979 648000 }
rpm   ConversionFactorToSI { 3.14159265358979 30 }

" ======================================================================================================================================================
" Energy units
" ======================================================================================================================================================

Define EnergyUnit      { J kWh }

kWh    ConversionFactorToSI { 3600000 }
J      ConversionFactorToSI { 1       }

" ======================================================================================================================================================
" Energy density units
" ======================================================================================================================================================

Define EnergyDensityUnit      { J/m3 kWh/m3 }

kWh/m3 ConversionFactorToSI { 3600000 }
J/m3   ConversionFactorToSI { 1       }

" ======================================================================================================================================================
" Specific Energy units
" ======================================================================================================================================================

Define SpecificEnergyUnit      { J/kg kWh/t }

kWh/t  ConversionFactorToSI { 3600000 1000 }
J/kg   ConversionFactorToSI { 1            }

" ======================================================================================================================================================
" Power units
" ======================================================================================================================================================

Define PowerUnit      { Watt kW MW}

MW     ConversionFactorToSI { 1000000 }
kW     ConversionFactorToSI { 1000 }
Watt   ConversionFactorToSI { 1    } Alias { W }

" ======================================================================================================================================================
" Cost units
" ======================================================================================================================================================

Define CostUnit      { $ }

$     ConversionFactorToSI { 1 }  Alias { dollars }

" ======================================================================================================================================================
" Cost rate units
" ======================================================================================================================================================

Define CostRateUnit      { $/s $/h $/d }

$/s    ConversionFactorToSI { 1 }       Alias { $/second }
$/h    ConversionFactorToSI { 1 3600  } Alias { $/hour }
$/d    ConversionFactorToSI { 1 86400 } Alias { $/day }

" ======================================================================================================================================================
" Linear Density units
" ======================================================================================================================================================

Define LinearDensityUnit      { t/m kt/m }

t/m  ConversionFactorToSI { 1000      }
kt/m ConversionFactorToSI { 1000000   }

" ======================================================================================================================================================
" Density units
" ======================================================================================================================================================

Define DensityUnit      { kg/m3 }

kg/m3  ConversionFactorToSI { 1 }

" ======================================================================================================================================================
" Pressure units
" ======================================================================================================================================================

Define PressureUnit      { Pa kPa psi }

Pa   ConversionFactorToSI { 1 }
kPa  ConversionFactorToSI { 1000 }
psi  ConversionFactorToSI { 6894.757 }

" ======================================================================================================================================================
" Viscosity units
" ======================================================================================================================================================

Define ViscosityUnit      { Pa-s P cP }

Pa-s  ConversionFactorToSI { 1 }
P     ConversionFactorToSI { 0.1 }
cP    ConversionFactorToSI { 0.001 }