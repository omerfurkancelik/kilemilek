package com.example.kilemilek.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kilemilek.R
import com.example.kilemilek.models.FriendModel
import de.hdodenhof.circleimageview.CircleImageView

class SelectFriendAdapter(
    private val context: Context,
    private val friends: List<FriendModel>,
    private val onFriendSelected: (FriendModel) -> Unit
) : RecyclerView.Adapter<SelectFriendAdapter.FriendViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_select_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]

        // Set friend name and email
        holder.nameTextView.text = friend.friendName.ifEmpty { "User" }
        holder.emailTextView.text = friend.friendEmail

        // Set online status (randomly for now)
        val isOnline = (position % 2 == 0)
        holder.statusTextView.text = if (isOnline) "Online" else "Offline"
        holder.statusTextView.setTextColor(context.resources.getColor(
            if (isOnline) R.color.online_green else R.color.text_secondary, null))

        // Set click listener for select button
        holder.selectButton.setOnClickListener {
            onFriendSelected(friend)
        }

        // Make the entire item clickable
        holder.itemView.setOnClickListener {
            onFriendSelected(friend)
        }
    }

    override fun getItemCount(): Int = friends.size

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImageView: CircleImageView = itemView.findViewById(R.id.friend_avatar)
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val emailTextView: TextView = itemView.findViewById(R.id.friend_email)
        val statusTextView: TextView = itemView.findViewById(R.id.friend_status)
        val selectButton: Button = itemView.findViewById(R.id.select_button)
    }
}