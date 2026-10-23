package com.cio.createinteroperable.thermal.cee;

import com.cio.createinteroperable.thermal.cpg.AirconMotorBottom240Block;

/**
 * The 240 V Electro Energetics-wired Aircon Motor — see
 * {@link AirconMotorBottom240Block}; identical idea, CEE-wired.
 */
public class CeeAirconMotorBottom240Block extends CeeAirconMotorBottomBlock {
    public CeeAirconMotorBottom240Block(Properties properties) {
        super(properties);
    }

    @Override
    public float ratedVolts() {
        return 240f;
    }
}
