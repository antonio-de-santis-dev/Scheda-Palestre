package com.gymplanner.workoutplan.internal;

import java.util.UUID;

/** Child of a plan ordered by a 1-based position inside its container. */
interface Positioned {

    UUID getId();

    int getPosition();

    void setPosition(int position);
}
