using System.ComponentModel;
using System.Windows;

namespace WaddleFree.Desktop.Models
{
    public class FaqItem : INotifyPropertyChanged
    {
        private bool _isExpanded;

        public string Question { get; init; } = string.Empty;
        public string Answer { get; init; } = string.Empty;

        public bool IsExpanded
        {
            get => _isExpanded;
            set
            {
                if (_isExpanded == value) return;
                _isExpanded = value;
                OnPropertyChanged(nameof(IsExpanded));
                OnPropertyChanged(nameof(AnswerVisibility));
                OnPropertyChanged(nameof(ChevronSymbol));
            }
        }

        public Visibility AnswerVisibility => _isExpanded ? Visibility.Visible : Visibility.Collapsed;
        public string ChevronSymbol => _isExpanded ? "▴" : "▾";

        public event PropertyChangedEventHandler? PropertyChanged;

        private void OnPropertyChanged(string propertyName)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }
}
