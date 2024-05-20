package com.example.staylocal.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.staylocal.R;
import com.example.staylocal.databinding.BusinessListItemBinding;
import com.example.staylocal.databinding.FilterSheetBinding;
import com.example.staylocal.databinding.FragmentHomeBinding;
import com.example.staylocal.fragments.models.Business;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class HomeFragment extends Fragment {

    public HomeFragment() {
        // Required empty public constructor
    }

    FragmentHomeBinding binding;
    HomeAdapter adapter;
    ListenerRegistration listenerRegistration;
    ArrayList<Business> mBusinesses = new ArrayList<>();
    private ArrayList<Business> originalBusinesses = new ArrayList<>();
    private ArrayList<Business> likedBusinesses = new ArrayList<>();

    ArrayList<String> mCategories = new ArrayList<>();
    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        binding.homeButton.setImageResource(R.drawable.home_icon_active);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new HomeAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        generateBusinesses();

        getLikedBusinesses();


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

        binding.filterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterAndSortMenu();
                binding.filterButton.setImageResource(R.drawable.filter_icon_active);

            }
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBusinesses(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

    }

    private RadioButton selectedRadioButton;
    private boolean isRatingSelected = false;
    private boolean isNoneSelected = false;
    ArrayList<String> selectedFilters = new ArrayList<>();

    private void filterBusinesses(String searchText) {
        ArrayList<Business> filteredList = new ArrayList<>();

        for (Business business : originalBusinesses) {
            // Perform case-insensitive search on business name
            if (business.getName().toLowerCase().contains(searchText.toLowerCase())) {
                filteredList.add(business);
            }
        }

        // Update the RecyclerView adapter with the filtered list
        adapter.filterList(filteredList);
    }
    private void showFilterAndSortMenu() {
        FilterSheetBinding filterSheetBinding = FilterSheetBinding.inflate(getLayoutInflater());
        PopupWindow popupWindow = new PopupWindow(filterSheetBinding.getRoot(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAsDropDown(binding.filterButton);

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                binding.filterButton.setImageResource(R.drawable.filter_icon);
            }
        });

        for (String category : mCategories) {
            CheckBox checkBox = new CheckBox(getContext());
            checkBox.setText(category);
            checkBox.setTextColor(getResources().getColor(android.R.color.white));
            checkBox.setTextSize(14);
            checkBox.setPadding(8, 8, 8, 8);

            if(selectedFilters.contains(category)){
                checkBox.setChecked(true);
            }

            filterSheetBinding.filtersLayout.addView(checkBox);
        }

        filterSheetBinding.sortOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // Update the selected radio button
                selectedRadioButton = filterSheetBinding.getRoot().findViewById(checkedId);
                // Update the flag based on the selected radio button
                isRatingSelected = (selectedRadioButton != null && selectedRadioButton.getId() == R.id.radioRating);
                isNoneSelected = (selectedRadioButton != null && selectedRadioButton.getId() == R.id.radioNone);
            }
        });

        // Set the selected RadioButton programmatically
        if (isRatingSelected) {
            filterSheetBinding.radioRating.setChecked(true);
        } else if(isNoneSelected){
            filterSheetBinding.radioNone.setChecked(true);
        }

        //Where the magic happens >:)
        filterSheetBinding.buttonApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check which radio button is selected and update
                processSorting(filterSheetBinding);
                //Check which filters are selected and upated
                processFilters(filterSheetBinding.filtersLayout);
                // Dismiss the popup window
                popupWindow.dismiss();
                binding.filterButton.setImageResource(R.drawable.filter_icon);

            }
        });


    }

    private void processSorting(FilterSheetBinding filterSheetBinding) {
        if (filterSheetBinding.radioRating.isChecked()) {
            Collections.sort(originalBusinesses, new Comparator<Business>() {
                @Override
                public int compare(Business business1, Business business2) {
                    return Double.compare(business2.getRating(), business1.getRating());
                }
            });
        } else if (filterSheetBinding.radioNone.isChecked()) {
            mBusinesses.clear();
            originalBusinesses.clear();
            generateBusinesses();
            adapter.notifyDataSetChanged();
        }
        adapter.notifyDataSetChanged();
    }

    private void processFilters(LinearLayout filtersLayout){
        //ArrayList to store selected Filters
        selectedFilters.clear();
        mBusinesses.clear();
        //Process selected filters and add them to the AL
        for (int i = 0; i < filtersLayout.getChildCount(); i++) {
            View view = filtersLayout.getChildAt(i);
            if (view instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) view;
                if (checkBox.isChecked()) {
                    selectedFilters.add(checkBox.getText().toString());
                }
            }
        }

        if(selectedFilters.isEmpty()){
            mBusinesses.clear();
            mBusinesses.addAll(originalBusinesses);
            adapter.notifyDataSetChanged();
        } else {
            for (Business business : originalBusinesses) {
                for (String category : business.getCategories()) {
                    if (selectedFilters.contains(category) && !mBusinesses.contains(business)) {
                        mBusinesses.add(business);
                    }
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    //Method to call YELP API and recieve business data
    //TODO: HIDE API KEY AND PARAMETERIZE THE NUMBER OF RESULTS SO THAT IT CAN BE CONTROLLED BY THE USER
    private void generateBusinesses() {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.yelp.com/v3/businesses/search?location=Charlotte%20NC&sort_by=best_match&limit=20")
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
                    mCategories.clear();

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
                            String hours = businessObject.optString("open_now", "N/A");

                            double rating = businessObject.getDouble("rating");

                            //format rating for easier img processing
                            rating = rating / 0.5;
                            int roundedIncrements = (int) Math.floor(rating);
                            double processedRating = roundedIncrements * 0.5;

                            String phone = businessObject.getString("phone");

                            String price = businessObject.optString("price", "N/A"); // If price is not available, default to "N/A"


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

                                if(!mCategories.contains(category)){
                                    mCategories.add(category);
                                }
                            }

                            mBusinesses.add(business);
                            originalBusinesses.add(business);



                            requireActivity().runOnUiThread(new Runnable() {
                                @SuppressLint("NotifyDataSetChanged")
                                @Override
                                public void run() {
                                    adapter.notifyDataSetChanged();
                                }
                            });

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        Document doc = Jsoup.connect("https://foodtruckscharlotte.org").get();

                        Elements truckElements = doc.select("div.eve_row");

                        for (Element truckElement : truckElements) {
                            if (truckElement.attr("day").equals("Today")) {
                                String name = truckElement.select("h3.eve_name a").text();
                                String address = truckElement.select("div.eve_st1").text();
                                String hours = truckElement.select("div.eve_time").text();
                                double latitude = Double.parseDouble(truckElement.attr("lat"));
                                double longitude = Double.parseDouble(truckElement.attr("lng"));
                                String imgURL = "https://foodtruckscharlotte.org" + truckElement.select("div.eve_pic img").attr("src");

                                Log.d("Food Truck", "Image: " + imgURL);

                                Business business = new Business(name, address, imgURL, hours, 0.0, "", null);
                                business.addCategory("Food Truck");

                                business.setLatitude(latitude);
                                business.setLongitude(longitude);

                                mBusinesses.add(business);
                                originalBusinesses.add(business);
                            }
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    // Handle unsuccessful response
                    System.out.println("Response failed: " + response.code() + " - " + response.message());
                }
                //add in custom business data at the bottom of list
                generateCustomBusinesses();
            }
        });
    }

    public void generateCustomBusinesses(){
        Log.d("CHECK", "generateCustomBusinesses: called");
        //DB query to add User created businesses
        listenerRegistration = db.collection("businesses")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if(error != null){
                            error.printStackTrace();
                            return;
                        }
                        for(QueryDocumentSnapshot doc : value){
                            Business custom_business = doc.toObject(Business.class);
                            if(!mBusinesses.contains(custom_business)){
                                mBusinesses.add(custom_business);
                                originalBusinesses.add(custom_business);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private boolean isBusinessLiked(Business business) {
        for (Business likedBusiness : likedBusinesses) {
            if (likedBusiness.getName().equals(business.getName())) {
                return true;
            }
        }
        return false;
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
                                String price = (String) businessData.get("price");


                                Business likedBusiness = new Business(name, address, imgURL, hours, rating, phone, price);
                                likedBusiness.setLatitude(latitude);
                                likedBusiness.setLongitude(longitude);
                                likedBusiness.setCategories(categories);

                                likedBusinesses.add(likedBusiness);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                });
    }
    public void addBusinesstoLikes(Business business){
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(userId);

            // Create a map representing the liked business
            HashMap<String, Object> likedBusinessMap = new HashMap<>();
            likedBusinessMap.put("name", business.getName());
            likedBusinessMap.put("address", business.getAddress());
            likedBusinessMap.put("imgURL", business.getImgURL());
            likedBusinessMap.put("hours", business.getHours());
            likedBusinessMap.put("rating", business.getRating());
            likedBusinessMap.put("phone", business.getPhone());
            likedBusinessMap.put("latitude", business.getLatitude());
            likedBusinessMap.put("longitude", business.getLongitude());
            likedBusinessMap.put("categories", business.getCategories());

            // Add the liked business to the user's array of liked businesses
            userRef.update("liked_businesses", FieldValue.arrayUnion(likedBusinessMap))
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("BUSINESS-ADDED", "Liked business added to user's document in Firebase");
                            // Optionally, you can notify the user that the business was added to their liked businesses
                            // For example: Toast.makeText(getContext(), "Business added to liked list!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("BUSINESS-ADDED", "Error updating user document with liked business", e);
                            // Handle error
                        }
                    });
        }
    }

    public void removeBusinessfromLikes(Business business, BusinessListItemBinding binding) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            DocumentReference userRef = db.collection("users").document(userId);

            userRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    ArrayList<HashMap<String, Object>> likedBusinessesData = (ArrayList<HashMap<String, Object>>) documentSnapshot.get("liked_businesses");
                    if (likedBusinessesData != null) {
                        // Remove the business from the liked businesses array
                        for (HashMap<String, Object> businessData : likedBusinessesData) {
                            if (businessData.get("name").equals(business.getName())) {
                                likedBusinessesData.remove(businessData);
                                break;
                            }
                        }
                        // Update the document with the modified liked businesses array
                        userRef.update("liked_businesses", likedBusinessesData)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        int removal_index = 0;
                                        Log.d("RemoveLikedBusiness", "Business removed from liked list");
                                        Iterator<Business> iterator = likedBusinesses.iterator();
                                        while (iterator.hasNext()) {
                                            Business likedBusiness = iterator.next();
                                            if (likedBusiness.getName().equals(business.getName())) {
                                                iterator.remove();
                                                binding.imageButtonLike.setImageResource(R.drawable.like_unactivated);
                                                adapter.notifyDataSetChanged();
                                            }
                                        }
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e("RemoveLikedBusiness", "Error updating user document with modified liked businesses array", e);
                                        // Handle failure
                                    }
                                });
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e("RemoveLikedBusiness", "Error retrieving user document", e);
                    // Handle failure
                }
            });
        }
    }

    //Adapter and ViewHolder to handle recycler view for Business listings
    class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.HomeViewHolder>{

        @NonNull
        @Override
        public HomeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            BusinessListItemBinding itemBinding = BusinessListItemBinding.inflate(getLayoutInflater(), parent, false);
            return new HomeViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull HomeViewHolder holder, int position) {
            holder.setupUI(mBusinesses.get(position));
        }

        @Override
        public int getItemCount() {
            return mBusinesses.size();
        }

        class HomeViewHolder extends RecyclerView.ViewHolder{
            BusinessListItemBinding itemBinding;
            Business mBusiness;

            public HomeViewHolder(BusinessListItemBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void setupUI(Business business) {
                mBusiness = business;
                itemBinding.businessName.setText(business.getName());
                itemBinding.businessAddress.setText(business.getAddress());
                itemBinding.businessPrice.setText(business.getPrice());

                Picasso.get().load(business.getImgURL()).into(itemBinding.businessPicture);

                double rating = business.getRating();

                if (rating == 0) {
                    itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_0);
                } else {
                    // Set rating image based on the actual rating value
                    if (rating >= 0.5 && rating < 1.0) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_0_half);
                    } else if (rating >= 1.0 && rating < 1.5) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_1_half);
                    } else if (rating >= 1.5 && rating < 2.0) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_1_half);
                    } else if (rating >= 2.0 && rating < 2.5) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_2);
                    } else if (rating >= 2.5 && rating < 3.0) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_2_half);
                    } else if (rating >= 3.0 && rating < 3.5) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_3);
                    } else if (rating >= 3.5 && rating < 4.0) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_3_half);
                    } else if (rating >= 4.0 && rating < 4.5) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_4);
                    } else if (rating >= 4.5 && rating < 5.0) {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_4_half);
                    } else {
                        itemBinding.imageViewRating.setImageResource(R.drawable.review_ribbon_small_5);
                    }
                }

                // Set click listener to navigate to business details
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showBusinessDetailsPopup(business);
                    }
                });
                if (isBusinessLiked(mBusiness)) {
                    itemBinding.imageButtonLike.setImageResource(R.drawable.like_activated);
                } else {
                    itemBinding.imageButtonLike.setImageResource(R.drawable.like_unactivated);
                }
                itemBinding.imageButtonLike.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isBusinessLiked(mBusiness)) {
                            removeBusinessfromLikes(mBusiness, itemBinding);
                        } else {
                            addBusinesstoLikes(mBusiness);
                        }
                    }
                });
            }

        }
        public void filterList(ArrayList<Business> filteredList) {
            mBusinesses = filteredList;
            notifyDataSetChanged();
        }
    }
    private void showBusinessDetailsPopup(Business business) {
        BusinessDetailsFragment bottomSheetFragment = BusinessDetailsFragment.newInstance(business);
        bottomSheetFragment.show(getChildFragmentManager(), bottomSheetFragment.getTag());
    }

    HomePageListener mListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if(context instanceof HomePageListener){
            mListener = (HomePageListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement HomePageListener");
        }
    }

    public interface HomePageListener{
        void gotoHome();
        void gotoProfile();
        void gotoMap();
        void gotoBusinessDetails(Business business);

        void gotoFoodTruck();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

}