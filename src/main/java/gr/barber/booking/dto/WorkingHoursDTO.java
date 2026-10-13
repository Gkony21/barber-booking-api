package gr.barber.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import gr.barber.booking.model.WorkingHours;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursDTO(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {

    public static WorkingHoursDTO from(WorkingHours hours) {
        return new WorkingHoursDTO(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
    }
}
