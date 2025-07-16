package com.petertill.szigmav;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class VehicleBottomSheet extends BottomSheetDialogFragment {

    private MainActivity.VehicleResponse.VehiclePosition vehicle;

    public VehicleBottomSheet(MainActivity.VehicleResponse.VehiclePosition vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_vehicle_info, container, false);

        TextView title = view.findViewById(R.id.train_title);
        TextView details = view.findViewById(R.id.train_details);

        if (vehicle != null && vehicle.trip != null) {
            title.setText(vehicle.trip.trainName != null ? vehicle.trip.trainName : "Ismeretlen vonat");

            StringBuilder sb = new StringBuilder();
            sb.append("🗺️ Úticél: ").append(vehicle.trip.tripHeadsign).append("\n");
            sb.append("🚆 Járat: ").append(vehicle.trip.tripShortName).append("\n");
            sb.append("🏷️ Kategória: ").append(vehicle.trip.trainCategoryName).append("\n");
            sb.append("⚡ Sebesség: ").append(vehicle.speed).append(" km/h\n");
            sb.append("🧭 Irány: ").append(vehicle.heading).append("°\n");
            sb.append("🕑 Max késés: ").append(getMaxDelay(vehicle)).append(" perc");

            details.setText(sb.toString());
        }

        return view;
    }

    private int getMaxDelay(MainActivity.VehicleResponse.VehiclePosition vehicle) {
        if (vehicle.trip == null || vehicle.trip.stoptimes == null || vehicle.trip.stoptimes.isEmpty()) {
            return 0;
        }

        int maxDelay = 0;
        for (MainActivity.VehicleResponse.VehiclePosition.StopTime st : vehicle.trip.stoptimes) {
            maxDelay = Math.max(maxDelay, Math.max(st.arrivalDelay, st.departureDelay));
        }
        return maxDelay / 60;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }
}
