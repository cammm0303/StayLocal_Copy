package com.example.staylocal.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staylocal.R;
import com.example.staylocal.databinding.BusinessListItemHorizontalBinding;
import com.example.staylocal.databinding.FragmentProfileBinding;
import com.example.staylocal.fragments.models.Business;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fragment responsible for displaying the user profile.
 */
public class ProfileFragment extends Fragment {

    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    ListenerRegistration listenerRegistration;
    FragmentProfileBinding binding;
    ProfileAdapter adapter;
    ArrayList<String> allCategories = new ArrayList<>();
    String recommendedCategory;
    ArrayList<Business> likedBusinesses = new ArrayList<>();
    ArrayList<Business> mRecomendations = new ArrayList<>();

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        binding.profileButton.setImageResource(R.drawable.profile_icon_active);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ProfileAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerView.setAdapter(adapter);

        binding.userName.setText(mAuth.getCurrentUser().getDisplayName());
        binding.userEmail.setText(mAuth.getCurrentUser().getEmail());

        //Get the users business to recommend
        getLikedBusinesses();

        // Navigation Buttons
        binding.buttonEvents.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.gotoNotifications();
            }
        });
        binding.buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.logout();
            }
        });

        //Nav Bar Button interactions
        binding.homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.gotoHome();
            }
        });

        binding.foodTruckButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.gotoFoodTruck();
            }
        });

        binding.profileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.gotoProfile();
            }
        });

        binding.mapsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.gotoMap();
            }
        });
    }

    private void showBusinessDetailsPopup(Business business) {
        BusinessDetailsFragment bottomSheetFragment = BusinessDetailsFragment.newInstance(business);
        bottomSheetFragment.show(getChildFragmentManager(), bottomSheetFragment.getTag());
    }

    private void getLikedBusinesses() {
        likedBusinesses.clear();
        listenerRegistration = db.collection("users").document(mAuth.getUid())
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                        if(error != null){
                            error.printStackTrace();
                            return;
                        }
                        ArrayList<HashMap<String, Object>> likedBusinessesData = (ArrayList<HashMap<String, Object>>) value.get("liked_businesses");
                        if (likedBusinessesData != null) {
                            for (HashMap<String, Object> businessData : likedBusinessesData) {
                                String name = (String) businessData.get("name");
                                String address = (String) businessData.get("address");
                                String imgURL = (String) businessData.get("imgURL");
                                String hours = (String) businessData.get("hours");
                                double rating = (double) businessData.get("rating");
                                String phone = (String) businessData.get("phone");
                                double latitude = (double) businessData.get("latitude");
                                double longitude = (double) businessData.get("longitude");
                                ArrayList<String> categories = (ArrayList<String>) businessData.get("categories");
                                String price = (String) businessData.get("Price");

                                Business likedBusiness = new Business(name, address, imgURL, hours, rating, phone, price);
                                likedBusiness.setLatitude(latitude);
                                likedBusiness.setLongitude(longitude);
                                likedBusiness.setCategories(categories);

                                likedBusinesses.add(likedBusiness);

                            }
                            getRecommendedCategory();
                        }
                    }
                });
    }
    private void getRecommendedCategory(){
        allCategories.clear();
        int maxCount = 0;
        //add all the categories the user likes to AL
        for (Business business : likedBusinesses) {
            for(String category: business.getCategories()){
                allCategories.add(category);
            }
        }
        //Find the # of times each category occurs
        HashMap<String, Integer> occurances = new HashMap<>();
        for (String category:allCategories) {
            if(occurances.get(category) == null){
                occurances.put(category,1);
            } else {
                int current_count = occurances.get(category);
                occurances.put(category, (++current_count));
            }
        }
        //find the most liked category
        for (HashMap.Entry<String, Integer> entry : occurances.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue();

            if (count > maxCount) {
                maxCount = count;
                recommendedCategory = category;
            }
        }
        Log.d("RECOMMENDEDCHECK", "Searching Complete: "+recommendedCategory);
        generateRecommendations(recommendedCategory);
    }
    private void generateRecommendations(String category) {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.yelp.com/v3/businesses/search?location=charlotte&categories="+category+"&sort_by=best_match&limit=20")
                .get()
                .addHeader("Authorization", "Bearer ndrKKA5Tw0RrHMUXGfjdA-xPP_eLUQQAjl9NWbl-TwbIdddd0GcHOmO45XjAXCYbsCLJl3u87yaaVPm3b1LppZtZ8lJiN3MsjuwlUdJWqh8tJfXTHeNrkG9Oa7r4ZXYx")
                .addHeader("accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    System.out.println("Response is processing");
                    String responseData = response.body().string();

                    try {
                        JSONObject jsonObject = new JSONObject(responseData);
                        JSONArray businessArray = jsonObject.getJSONArray("businesses");

                        for (int i = 0; i < businessArray.length(); i++) {
                            JSONObject businessObject = businessArray.getJSONObject(i);

                            String name = businessObject.getString("name");
                            String address = businessObject.getJSONObject("location").getString("address1");
                            //TODO: ADD IN LOGIC TO DISPLAY IMAGE IF AVALIABLE, ELSE DISPLAY DEFAULT IMAGE
                            String imgURL = "";
                            if(businessObject.getString("image_url") != ""){
                                imgURL = businessObject.getString("image_url");
                            }
                            String hours = "Hours Not listed on YELP";
                            double rating = businessObject.getDouble("rating");

                            //format rating for easier img processing
                            rating = rating / 0.5;
                            int roundedIncrements = (int) Math.floor(rating);
                            double processedRating = roundedIncrements * 0.5;

                            String phone = businessObject.getString("phone");
                            String price = businessObject.optString("price", "N/A");

                            Business business = new Business(name, address, imgURL, hours, processedRating, phone, price);

                            //set lat long data for business
                            JSONObject coordinates = businessObject.getJSONObject("coordinates");
                            business.setLatitude(coordinates.getDouble("latitude"));
                            business.setLongitude(coordinates.getDouble("longitude"));

                            //Add categories to business
                            JSONArray categoriesArray = businessObject.getJSONArray("categories");
                            for (int j=0; j<categoriesArray.length(); j++){
                                JSONObject categoryObject = categoriesArray.getJSONObject(j);
                                String category = categoryObject.getString("alias");
                                business.addCategory(category);
                            }

                            mRecomendations.add(business);

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.notifyDataSetChanged();
                                }
                            });

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Handle unsuccessful response
                    System.out.println("Response failed: " + response.code() + " - " + response.message());
                }
            }
        });
    }

    class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>{
        @NonNull
        @Override
        public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            BusinessListItemHorizontalBinding itemBinding = BusinessListItemHorizontalBinding.inflate(getLayoutInflater(), parent, false);
            return new ProfileViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
            holder.setupUI(mRecomendations.get(position));
        }

        @Override
        public int getItemCount() {
            return mRecomendations.size();
        }

        class ProfileViewHolder extends RecyclerView.ViewHolder{
            BusinessListItemHorizontalBinding itemBinding;
            Business mBusiness;

            public ProfileViewHolder(BusinessListItemHorizontalBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void setupUI(Business business){
                mBusiness = business;
                itemBinding.businessName.setText(business.getName());
                itemBinding.businessAddress.setText(business.getAddress());
                Picasso.get().load(business.getImgURL()).into(itemBinding.businessPicture);

                if(business.getRating() == 0){
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_0);
                } else if (business.getRating() == 0.5) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_0_half);
                } else if (business.getRating() == 1) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_1_half);
                } else if (business.getRating() == 1.5) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_1_half);
                }  else if (business.getRating() == 2) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_2);
                } else if (business.getRating() == 2.5) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_2_half);
                } else if (business.getRating() == 3) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_3);
                } else if (business.getRating() == 3.5) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_3_half);
                } else if (business.getRating() == 4) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_4);
                } else if (business.getRating() == 4.5) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_4_half);
                } else {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_5);
                }

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showBusinessDetailsPopup(business);
                    }
                });
            }
        }
    }
    ProfileListener mListener;
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ProfileListener) {
            mListener = (ProfileListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ProfileListener");
        }
    }

    public interface ProfileListener {
        void logout();
        void gotoNotifications();
        void gotoBusinessDetails(Business business);
        void gotoHome();
        void gotoFoodTruck();
        void gotoProfile();
        void gotoMap();
    }
}
