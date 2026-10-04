package com.cio.createinteroperable;

/**
 * The 240 V Power Grid-wired Aircon Motor: the same block as
 * {@link AirconMotorBottomBlock} (same model, terminals, pairing with the fan)
 * but a separate item/recipe, rated for twice the voltage — see
 * {@link AirconMotorBottomBlockEntity}, which scales its voltage bands,
 * resistance and output off {@link #ratedVolts()}.
 */
public class AirconMotorBottom240Block extends AirconMotorBottomBlock {
    public AirconMotorBottom240Block(Properties properties) {
        super(properties);
    }

    @Override
    public float ratedVolts() {
        return 240f;
    }
}
